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
