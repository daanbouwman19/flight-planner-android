import { FlightPlannerTheme, VisitedNetworkCard } from '@flightplanner/design-mirror'

// Real coordinates, read out of the app's own `airports.db`. Nothing here is
// invented: a plausible-looking field in the wrong place is a confidently wrong
// drawing, and this map exists to be read.
const LOW_COUNTRIES = [
  { icao: 'EHAM', lat: 52.308601, lon: 4.76389, visits: 14 },
  { icao: 'EHRD', lat: 51.956902, lon: 4.43722, visits: 5 },
  { icao: 'EHEH', lat: 51.4501, lon: 5.37453, visits: 3 },
  { icao: 'EHGG', lat: 53.119107, lon: 6.577652, visits: 2 },
  { icao: 'EBBR', lat: 50.901402, lon: 4.48444, visits: 4 },
]

const EUROPE = [
  ...LOW_COUNTRIES,
  { icao: 'EGLL', lat: 51.470748, lon: -0.459909, visits: 9 },
  { icao: 'EGKK', lat: 51.148744, lon: -0.185739, visits: 2 },
  { icao: 'EDDF', lat: 50.026706, lon: 8.55835, visits: 6 },
  { icao: 'LFPG', lat: 49.00896, lon: 2.554117, visits: 5 },
  { icao: 'LSZH', lat: 47.458056, lon: 8.548056, visits: 3 },
  { icao: 'LOWW', lat: 48.110298, lon: 16.5697, visits: 2 },
  { icao: 'LIRF', lat: 41.804532, lon: 12.251998, visits: 3 },
  { icao: 'LEMD', lat: 40.493407, lon: -3.572249, visits: 4 },
  { icao: 'EKCH', lat: 55.6179, lon: 12.656, visits: 2 },
  { icao: 'ESSA', lat: 59.64849, lon: 17.928829, visits: 1 },
]

const leg = (a: string, b: string) => {
  const from = EUROPE.find((x) => x.icao === a)!
  const to = EUROPE.find((x) => x.icao === b)!
  return { from: [from.lat, from.lon] as [number, number], to: [to.lat, to.lon] as [number, number] }
}

/**
 * A logbook that has spread across a continent.
 *
 * The legs are great-circle arcs sampled by the same `MapFrame` the route card
 * projects through, so a network drawn here and a route drawn on a card are the
 * same world at two zooms rather than two maps that happen to look alike.
 */
export const Europe = () => (
  <div style={{ width: 380 }}>
    <VisitedNetworkCard
      airports={EUROPE}
      legs={[
        leg('EHAM', 'EGLL'),
        leg('EHAM', 'EDDF'),
        leg('EHAM', 'LEMD'),
        leg('EHAM', 'ESSA'),
        leg('EDDF', 'LIRF'),
        leg('EBBR', 'LOWW'),
      ]}
    />
  </div>
)

/**
 * A logbook that has not left the Low Countries.
 *
 * The frame is fitted to the visited set rather than to the globe — but only down to
 * `MIN_SPAN_DEGREES`, the 25° floor `MapFrame` applies to a route as well. So this
 * still shows north-west Europe with the fields clustered in the middle, which is the
 * intended reading: below about that span the coastline stops being recognisable.
 * Compare it with `Europe` above — same projection, same floor, a genuinely wider
 * set of markers.
 *
 * The marker radius goes as √visits, so a field flown fourteen times reads as busier
 * than one flown twice without swamping the map.
 */
export const OneRegion = () => (
  <div style={{ width: 380 }}>
    <VisitedNetworkCard
      airports={LOW_COUNTRIES}
      legs={[leg('EHAM', 'EHRD'), leg('EHRD', 'EBBR'), leg('EHAM', 'EHGG')]}
    />
  </div>
)

/** Nothing logged yet — a line rather than an empty world. */
export const NothingLogged = () => (
  <div style={{ width: 380 }}>
    <VisitedNetworkCard airports={[]} />
  </div>
)

/** In Chart, where the coastline is ink on paper. */
export const Chart = () => (
  <FlightPlannerTheme theme="chart">
    <div style={{ width: 380 }}>
      <VisitedNetworkCard airports={EUROPE} legs={[leg('EHAM', 'LEMD'), leg('EHAM', 'ESSA')]} />
    </div>
  </FlightPlannerTheme>
)
