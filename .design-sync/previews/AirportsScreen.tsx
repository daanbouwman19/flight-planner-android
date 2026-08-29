import { AirportsScreen } from '@flightplanner/design-mirror'

const airports = [
  { icao: 'EHAM', name: 'Amsterdam Airport Schiphol', location: 'Amsterdam, NL', longestRunway: '12,467 ft', rules: 'VFR' as const },
  { icao: 'EHRD', name: 'Rotterdam The Hague Airport', location: 'Rotterdam, NL', longestRunway: '7,218 ft', rules: 'MVFR' as const },
  { icao: 'EBBR', name: 'Brussels Airport', location: 'Zaventem, BE', longestRunway: '11,936 ft', rules: 'VFR' as const },
  { icao: 'EGLL', name: 'London Heathrow Airport', location: 'London, GB', longestRunway: '12,799 ft', rules: 'IFR' as const },
  { icao: 'EDDF', name: 'Frankfurt Main Airport', location: 'Frankfurt am Main, DE', longestRunway: '13,123 ft', rules: 'VFR' as const },
  { icao: 'LFPG', name: 'Charles de Gaulle International Airport', location: 'Paris, FR', longestRunway: '13,829 ft', rules: 'LIFR' as const },
]

/** Browsing the dataset, reachable from Plan's header. */
export const Browse = () => <AirportsScreen airports={airports} />

/** Mid-search: the query narrows the list as it is typed. */
export const Searching = () => (
  <AirportsScreen airports={airports.filter((a) => a.icao.startsWith('EH'))} query="EH" />
)

/** The wide window, with the rail carrying Settings alongside the four sections. */
export const Tablet = () => <AirportsScreen airports={airports} layout="tablet" />
