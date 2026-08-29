import { SkyProfile, SkyProfileHeight } from '@flightplanner/design-mirror'

/**
 * The headline case: a 700 ft overcast.
 *
 * The deck sits below the 1,000 ft hairline, so the field is visibly IFR without
 * anything saying so. This is the arrangement the whole redesign exists for — the
 * category is a consequence of the geometry rather than a label pinned beside a
 * cartoon, so there is no way to draw this as a nice day.
 */
export const LowOvercast = () => (
  <div style={{ width: 360 }}>
    <SkyProfile
      skyCover={{ kind: 'layers', layers: [{ cover: 'OVERCAST', baseFt: 700 }] }}
      ceilingFt={700}
    />
  </div>
)

/** A clear afternoon: scattered fair-weather cumulus, sun up, no ceiling. */
export const ClearDay = () => (
  <div style={{ width: 360 }}>
    <SkyProfile
      skyCover={{ kind: 'layers', layers: [{ cover: 'FEW', baseFt: 4500 }] }}
      ceilingFt={null}
      celestial={{ sunElevationDeg: 42, sunAzimuthDeg: 210 }}
    />
  </div>
)

/**
 * A half-mile fog: `FG VV002`.
 *
 * The slab is opaque with a hard top edge rather than a fade. Drawn as a gradient
 * it met the sky at about 1.1:1 and this looked like a clear day with a wash at
 * the bottom — fog is not a tint on the air, it is a surface.
 */
export const Fog = () => (
  <div style={{ width: 360 }}>
    <SkyProfile
      skyCover={{ kind: 'obscured', verticalVisibilityFt: 200 }}
      fogOrMist
      visibilityStatuteMiles={0.5}
      ceilingFt={200}
    />
  </div>
)

/** Several decks with a thunderstorm in the middle one. */
export const Convective = () => (
  <div style={{ width: 360 }}>
    <SkyProfile
      skyCover={{
        kind: 'layers',
        layers: [
          { cover: 'SCATTERED', baseFt: 2500 },
          { cover: 'BROKEN', baseFt: 4300, convective: 'CB' },
          { cover: 'FEW', baseFt: 22000 },
        ],
      }}
      ceilingFt={4300}
    />
  </div>
)

/**
 * No report at all.
 *
 * The air hatches rather than being painted a plausible grey: painting anything
 * asserts the sky is known, and a report that mentions no sky could be hiding a
 * 200 ft overcast.
 */
export const NoReport = () => (
  <div style={{ width: 360 }}>
    <SkyProfile skyCover={{ kind: 'unknown' }} height={SkyProfileHeight.RouteDetail} />
  </div>
)

/** Night, with a broken deck dimming the moon. */
export const NightBroken = () => (
  <div style={{ width: 360 }}>
    <SkyProfile
      phase="NIGHT"
      skyCover={{ kind: 'layers', layers: [{ cover: 'BROKEN', baseFt: 2200 }] }}
      ceilingFt={2200}
      celestial={{ sunElevationDeg: -28, sunAzimuthDeg: 20, moonElevationDeg: 34, moonAzimuthDeg: 250 }}
    />
  </div>
)
