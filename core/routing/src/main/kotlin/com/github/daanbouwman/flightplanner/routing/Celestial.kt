package com.github.daanbouwman.flightplanner.routing

/**
 * Where a body stands in the sky, as seen from one field.
 *
 * [elevationDeg] is **geometric and topocentric**: measured from the true
 * horizontal plane, unrefracted, and corrected for the observer standing on the
 * surface rather than at the centre of the Earth. Unrefracted is a decision rather
 * than a shortcut — the twilight thresholds this feeds are themselves defined on
 * geometric elevation, so refracting first would make the app disagree with the
 * definition it is quoting. See [solarPosition] for the rest of that argument.
 *
 * [azimuthDeg] is 0..360 from **true north, clockwise** — the same convention as a
 * runway heading, and deliberately not Meeus's from-south one. It is
 * ill-conditioned near the zenith, where every azimuth converges: above about 85°
 * of elevation it should not be read, and nothing should assert it.
 */
data class CelestialBody(val elevationDeg: Double, val azimuthDeg: Double) {

    /**
     * Whether the body's centre is above the geometric horizon.
     *
     * **Not "is it daytime".** Sunrise is conventionally the moment the *upper
     * limb* clears a *refracted* horizon, which is 0.833° lower, and the sky stays
     * lit for the better part of an hour after this turns false. What the scene is
     * painted in is a separate question with its own function; this one is for
     * deciding whether to draw a disc at all.
     */
    val isUp: Boolean get() = elevationDeg > 0.0
}

/**
 * Everything the sky scene needs about the Sun and the Moon at one instant at one
 * field.
 *
 * [moonIlluminatedFraction] is 0 at new and 1 at full, and it is the fraction of
 * the visible **disc** that is lit — *not* the fraction of the elongation. A
 * quarter moon is half lit at 90° of elongation, and because *k* moves with a
 * cosine it barely changes for two days either side of full. Geocentric; the
 * topocentric value differs by under 0.3 % near the horizon, which no terminator
 * can show.
 *
 * [moonWaxing] is true while the illuminated limb is the trailing one. Between them
 * the two place a vertical terminator on an upright disc. The scene deliberately
 * does not *tilt* that disc: a schematic cross-section has no sky orientation to
 * tilt against, so the position angle of the bright limb (Meeus 48.5) would be a
 * number with nowhere to go. A viewer who looks up will see a different tilt, and
 * that divergence is the price of drawing a profile view rather than a sky.
 */
data class CelestialState(
    val sun: CelestialBody,
    val moon: CelestialBody,
    val moonIlluminatedFraction: Double,
    val moonWaxing: Boolean,
    /**
     * The latitude this state was computed for.
     *
     * Carried because [moonWaxing] alone cannot place the terminator: **a waxing
     * moon is lit on the right in Amsterdam and on the left in Santiago.** Which
     * limb is lit is an astronomical fact about the observer, not a rendering
     * choice, so it belongs here rather than being rediscovered by a renderer that
     * would have to be handed a latitude anyway. See [moonLitLimbOnRight].
     *
     * It is not a redundant copy of the caller's own input: [sun] and [moon] are
     * already topocentric — this whole state is *as seen from here* — and the
     * latitude is part of what "here" means.
     */
    val observerLatitudeDeg: Double,
) {
    /**
     * Which side of the disc is lit, as a renderer needs it.
     *
     * Waxing puts the lit limb on the right in the northern hemisphere and on the
     * left in the southern, because an observer south of the equator is, in
     * effect, looking at the same sky upside down.
     *
     * The scene does not *tilt* the disc — a schematic cross-section has no sky
     * orientation to tilt against, so the position angle of the bright limb (Meeus
     * 48.5) would be a number with nowhere to go. But which limb is lit is not a
     * tilt, it is a fact, and it is wrong for half the world if ignored.
     */
    val moonLitLimbOnRight: Boolean get() = moonWaxing != (observerLatitudeDeg < 0.0)
}

/**
 * Where the Sun and the Moon stand, and what phase the Moon is in, at one field at
 * one instant.
 *
 * Pure JVM and `Double` throughout — see [CelestialFrames.kt][julianDay] on why
 * this diverges from [GreatCircle]'s deliberate `Float`.
 *
 * ### Polar cases need nothing from a caller
 *
 * Elevation is `asin(sin φ sin δ + cos φ cos δ cos H)`, which is defined at every
 * latitude on every date. What does not exist at `PABR` in December is a **rise
 * time**, and this returns none. That is the whole reason the scene keys off
 * elevation rather than off sunrise and sunset: a sun that is −4.7° at local noon
 * and −42° at local midnight has no crossing to ask about, and the drawing still
 * knows exactly what to paint.
 *
 * ### Where to call it
 *
 * Costs about 45 sines and 20 cosines — cheap, but call it from a ViewModel or a
 * `remember`, never from `Application.onCreate`, a Hilt `@Singleton` constructor or
 * an `androidx.startup` Initializer. The cold-start budget in `CLAUDE.md` is spent
 * before first frame, and this feature's own plan already records a 646 ms
 * regression from exactly that reflex.
 */
object Celestial {

    /**
     * [longitudeDeg] is **east-positive**, matching the airport dataset and
     * `Metar.longitude`. [epochSeconds] is Unix seconds, treated as both UT and
     * Terrestrial Time — see [julianCenturies] for the measured cost of skipping
     * ΔT.
     */
    fun at(latitudeDeg: Double, longitudeDeg: Double, epochSeconds: Long): CelestialState {
        val jd = julianDay(epochSeconds)
        val t = julianCenturies(jd)
        val obliquity = apparentObliquityDeg(t)

        val sun = solarPosition(t)
        // The Sun's ecliptic latitude never exceeds 1.2 arcseconds, so zero here is
        // not a simplification worth flagging at the call site.
        val sunEquatorial = equatorialFromEcliptic(sun.apparentLongitudeDeg, 0.0, obliquity)
        val sunHorizontal = horizontalFrom(
            equatorial = sunEquatorial,
            latitudeDeg = latitudeDeg,
            hourAngleDeg = localHourAngleDeg(sunEquatorial, longitudeDeg, jd),
        )

        val moon = lunarPosition(t)
        val moonEquatorial = equatorialFromEcliptic(moon.longitudeDeg, moon.latitudeDeg, obliquity)
        val moonHorizontal = horizontalFrom(
            equatorial = moonEquatorial,
            latitudeDeg = latitudeDeg,
            hourAngleDeg = localHourAngleDeg(moonEquatorial, longitudeDeg, jd),
        )

        val phase = lunarPhase(
            moon = moon,
            sunApparentLongitudeDeg = sun.apparentLongitudeDeg,
            sunDistanceKm = sun.radiusVectorKm,
        )

        // Both bodies through the same parallax correction, so `elevationDeg` means
        // one thing. It is worth about a degree for the Moon and a fiftieth of a
        // pixel for the Sun; see `topocentricElevationDeg`.
        return CelestialState(
            sun = sunHorizontal.copy(
                elevationDeg = topocentricElevationDeg(sunHorizontal.elevationDeg, sun.radiusVectorKm),
            ),
            moon = moonHorizontal.copy(
                elevationDeg = topocentricElevationDeg(moonHorizontal.elevationDeg, moon.distanceKm),
            ),
            moonIlluminatedFraction = phase.illuminatedFraction,
            moonWaxing = phase.waxing,
            observerLatitudeDeg = latitudeDeg,
        )
    }
}
