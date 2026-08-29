import { FleetScreen } from '@flightplanner/design-mirror'

const fleet = [
  { variant: 'Cessna 172S Skyhawk', category: 'Single Engine Piston', range: '640 NM', requiredRunway: '1,685 ft', flights: 42 },
  { variant: 'Diamond DA40 NG', category: 'Single Engine Piston', range: '940 NM', requiredRunway: '1,969 ft', flights: 17 },
  { variant: 'Cirrus SR22T', category: 'Single Engine Piston', range: '1,021 NM', requiredRunway: '1,082 ft', flights: 9 },
  { variant: 'Pilatus PC-12 NGX', category: 'Single Engine Turboprop', range: '1,803 NM', requiredRunway: '2,438 ft', flights: 6 },
  { variant: 'Beechcraft King Air 350i', category: 'Multi Engine Turboprop', range: '1,806 NM', requiredRunway: '3,300 ft' },
  { variant: 'Cessna Citation CJ4', category: 'Jet', range: '2,165 NM', requiredRunway: '3,410 ft', flights: 3 },
  { variant: 'Boeing 777-300ER', category: 'Jet', range: '7,370 NM', requiredRunway: '10,200 ft', flights: 1 },
]

/** The whole fleet, every category. Each row states the envelope, because those two numbers are what constrain a generated route. */
export const AllCategories = () => <FleetScreen aircraft={fleet} selectedCategory={0} />

/** Filtered to piston singles — the aircraft most routes are actually generated for. */
export const PistonOnly = () => (
  <FleetScreen aircraft={fleet.slice(0, 3)} selectedCategory={1} />
)
