import { FlightPlannerTheme, StatsScreen } from '@flightplanner/design-mirror'

const props = {
  totalDistance: '48,213 NM',
  earthCircumferences: '2.2 × around the Earth',
  metrics: [
    { label: 'FLIGHTS', value: '42' },
    { label: 'HOURS', value: '63:25' },
    { label: 'AIRPORTS', value: '31' },
    { label: 'LONGEST', value: '3,153 NM' },
  ],
  monthly: [
    { label: 'M', value: 2 }, { label: 'J', value: 5 }, { label: 'J', value: 7 },
    { label: 'A', value: 9 }, { label: 'S', value: 4 }, { label: 'O', value: 6 },
  ],
  topAircraft: [
    { name: 'Cessna 172S Skyhawk', flights: 18 },
    { name: 'Diamond DA40 NG', flights: 11 },
    { name: 'Cirrus SR22T', flights: 7 },
  ],
  topAirports: [
    { icao: 'EHAM', name: 'Schiphol', visits: 14 },
    { icao: 'EGLL', name: 'Heathrow', visits: 9 },
    { icao: 'EDDF', name: 'Frankfurt', visits: 6 },
  ],
  // Real coordinates, read out of the app's own airports.db. A plausible-looking
  // field in the wrong place is a confidently wrong drawing.
  visited: [
    { icao: 'EHAM', lat: 52.308601, lon: 4.76389, visits: 14 },
    { icao: 'EGLL', lat: 51.470748, lon: -0.459909, visits: 9 },
    { icao: 'EDDF', lat: 50.026706, lon: 8.55835, visits: 6 },
    { icao: 'LFPG', lat: 49.00896, lon: 2.554117, visits: 5 },
    { icao: 'EBBR', lat: 50.901402, lon: 4.48444, visits: 4 },
    { icao: 'LSZH', lat: 47.458056, lon: 8.548056, visits: 3 },
    { icao: 'EKCH', lat: 55.6179, lon: 12.656, visits: 2 },
  ],
  visitedLegs: [
    { from: [52.308601, 4.76389] as [number, number], to: [51.470748, -0.459909] as [number, number] },
    { from: [52.308601, 4.76389] as [number, number], to: [50.026706, 8.55835] as [number, number] },
    { from: [52.308601, 4.76389] as [number, number], to: [55.6179, 12.656] as [number, number] },
    { from: [50.901402, 4.48444] as [number, number], to: [47.458056, 8.548056] as [number, number] },
  ],
}

/** The headline figure, four metrics, and what the logbook adds up to. */
export const Default = () => <StatsScreen {...props} />

/** In Chart — the theme that is deliberately a printed page rather than a light app. */
export const Chart = () => (
  <FlightPlannerTheme theme="chart">
    <StatsScreen {...props} selectedRange={1} />
  </FlightPlannerTheme>
)

/** The wide window: a rail, and the content capped rather than stretched. */
export const Tablet = () => <StatsScreen {...props} layout="tablet" />
