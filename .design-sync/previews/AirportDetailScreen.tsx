import { AirportDetailScreen } from '@flightplanner/design-mirror'

// Schiphol's runway thresholds are the real published coordinates, from the app's
// own airports.db.
const schiphol = [
  { ident: '18R', lengthFt: 12467, widthFt: 198, trueHeadingDeg: 183, latitude: 52.3627, longitude: 4.71193, hardSurface: true },
  { ident: '36L', lengthFt: 12467, widthFt: 198, trueHeadingDeg: 3, latitude: 52.3286, longitude: 4.70884, hardSurface: true },
  { ident: '06', lengthFt: 11283, widthFt: 148, trueHeadingDeg: 58, latitude: 52.2879, longitude: 4.73402, hardSurface: true },
  { ident: '24', lengthFt: 11283, widthFt: 148, trueHeadingDeg: 238, latitude: 52.3046, longitude: 4.77752, hardSurface: true },
  { ident: '09', lengthFt: 11329, widthFt: 148, trueHeadingDeg: 87, latitude: 52.3166, longitude: 4.74635, hardSurface: true },
  { ident: '27', lengthFt: 11329, widthFt: 148, trueHeadingDeg: 267, latitude: 52.3184, longitude: 4.79689, hardSurface: true },
]

/** A fine day: a few fair-weather cumulus, sun up, a westerly picking runway 24. */
export const ClearDay = () => (
  <AirportDetailScreen
    icao="EHAM"
    name="Amsterdam Airport Schiphol"
    location="Amsterdam, Netherlands"
    elevation="-11 ft"
    rules="VFR"
    runways={schiphol}
    wind={{ directionFromDeg: 240, speedKt: 12 }}
    skyCover={{ kind: 'layers', layers: [{ cover: 'FEW', baseFt: 3500 }] }}
    ceilingFt={null}
    celestial={{ sunElevationDeg: 38, sunAzimuthDeg: 195 }}
    metar="EHAM 291025Z 24012KT 9999 FEW035 18/11 Q1018 NOSIG"
  />
)

/**
 * A 400 ft overcast in fog: the case the whole weather redesign exists for.
 *
 * The deck sits below the 500 ft hairline and the surface slab is opaque, so the
 * field reads as LIFR from the geometry rather than from the chip.
 */
export const LowIfr = () => (
  <AirportDetailScreen
    icao="EHAM"
    name="Amsterdam Airport Schiphol"
    location="Amsterdam, Netherlands"
    elevation="-11 ft"
    rules="LIFR"
    runways={schiphol}
    wind={{ directionFromDeg: 60, speedKt: 5 }}
    skyCover={{ kind: 'layers', layers: [{ cover: 'OVERCAST', baseFt: 400 }] }}
    fogOrMist
    visibilityStatuteMiles={0.4}
    ceilingFt={400}
    celestial={{ sunElevationDeg: 9, sunAzimuthDeg: 120 }}
    metar="EHAM 290655Z 06005KT 0800 FG OVC004 09/09 Q1024"
  />
)

/** No report at all — the air hatches rather than being painted a plausible grey. */
export const NoWeather = () => (
  <AirportDetailScreen
    icao="EHRD"
    name="Rotterdam The Hague Airport"
    location="Rotterdam, Netherlands"
    elevation="-15 ft"
    runways={[
      { ident: '06', lengthFt: 7218, widthFt: 148, trueHeadingDeg: 57, hardSurface: true },
      { ident: '24', lengthFt: 7218, widthFt: 148, trueHeadingDeg: 237, hardSurface: true },
    ]}
    skyCover={{ kind: 'unknown' }}
  />
)
