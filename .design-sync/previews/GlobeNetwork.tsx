import { GlobeNetwork } from '@flightplanner/design-mirror'

// Real coordinates from the app's own airports.db.
const EUROPE = [
  { icao: 'EHAM', lat: 52.308601, lon: 4.76389, visits: 14 },
  { icao: 'EGLL', lat: 51.470748, lon: -0.459909, visits: 9 },
  { icao: 'EDDF', lat: 50.026706, lon: 8.55835, visits: 6 },
  { icao: 'LFPG', lat: 49.00896, lon: 2.554117, visits: 5 },
  { icao: 'EBBR', lat: 50.901402, lon: 4.48444, visits: 4 },
  { icao: 'LEMD', lat: 40.493407, lon: -3.572249, visits: 4 },
  { icao: 'LIRF', lat: 41.804532, lon: 12.251998, visits: 3 },
  { icao: 'ESSA', lat: 59.64849, lon: 17.928829, visits: 1 },
]

const WORLD = [
  ...EUROPE,
  { icao: 'KJFK', lat: 40.639447, lon: -73.779317, visits: 7 },
  { icao: 'KLAX', lat: 33.942536, lon: -118.408075, visits: 3 },
  { icao: 'RJTT', lat: 35.552258, lon: 139.779694, visits: 2 },
  { icao: 'YSSY', lat: -33.946111, lon: 151.177222, visits: 1 },
  { icao: 'FACT', lat: -33.964806, lon: 18.601667, visits: 1 },
]

const leg = (set: typeof WORLD, a: string, b: string) => {
  const from = set.find((x) => x.icao === a)!
  const to = set.find((x) => x.icao === b)!
  return { from: [from.lat, from.lon] as [number, number], to: [to.lat, to.lon] as [number, number] }
}

const band = (node: React.ReactNode) => (
  <div style={{ width: 340, height: 260 }}>{node}</div>
)

/**
 * Stats' visited network in its Globe state — every airport visited, every leg
 * flown, on the sphere. The camera frames the whole visited set (`framePoints`),
 * node radius goes as √visits, and dots on the far side are culled at the limb.
 *
 * For a regional logbook the flat map is the better drawing; this is the one for
 * a network that has outgrown a rectangle.
 */
export const Continental = () =>
  band(
    <GlobeNetwork
      airports={EUROPE}
      legs={[leg(EUROPE, 'EHAM', 'EGLL'), leg(EUROPE, 'EHAM', 'LEMD'), leg(EUROPE, 'EHAM', 'ESSA'), leg(EUROPE, 'EDDF', 'LIRF')]}
    />,
  )

/**
 * A logbook that spans hemispheres. On a flat map these airports collapse into
 * clusters at the edges with the arcs crossing an empty middle; here the same
 * set is a planet turned to show all of it, and each leg is the short way round.
 */
export const Hemispheres = () =>
  band(
    <GlobeNetwork
      airports={WORLD}
      legs={[
        leg(WORLD, 'EHAM', 'KJFK'),
        leg(WORLD, 'KJFK', 'KLAX'),
        leg(WORLD, 'KLAX', 'RJTT'),
        leg(WORLD, 'RJTT', 'YSSY'),
        leg(WORLD, 'EHAM', 'FACT'),
      ]}
    />,
  )
