import { NavigationBar, PhoneFrame } from '../components/AppChrome'
import { FlightRulesBadge, type FlightRules } from '../components/FlightRulesBadge'
import { ModeSelector } from '../components/ModeSelector'

export interface AirportRow {
  icao: string
  name: string
  /** `Amsterdam, Netherlands`. */
  location: string
  /** Already formatted — `12,467 ft`. */
  longestRunway: string
  rules?: FlightRules
}

export interface AirportsScreenProps {
  airports: AirportRow[]
  /** What is typed in the search field. */
  query?: string
  selectedFilter?: number
  className?: string
}

/**
 * Airports: browse the dataset, reachable from Plan's header.
 *
 * Rows carry the ICAO code first because that is what the rest of the app
 * identifies an airport by, and the code is a chart figure — tabular, and left to
 * right whatever the app's language is.
 */
export function AirportsScreen({
  airports,
  query = '',
  selectedFilter = 0,
  className,
}: AirportsScreenProps) {
  return (
    <PhoneFrame className={className} bottomBar={<NavigationBar selected="plan" />}>
      <div className="fp-screen">
        <div className="fp-screen__header">
          <h1 className="fp-screen__title fp-type-headline-medium">Airports</h1>
        </div>

        <div className="fp-screen__controls">
          <div className="fp-screen__search">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z" />
            </svg>
            <span className="fp-type-body-large">{query === '' ? 'Search airports' : query}</span>
          </div>
          <ModeSelector
            options={[{ label: 'All' }, { label: 'Hard surface' }, { label: 'Visited' }]}
            selectedIndex={selectedFilter}
          />
        </div>

        <div className="fp-screen__list">
          {airports.map((airport) => (
            <button type="button" className="fp-screen__row" key={airport.icao}>
              <div className="fp-screen__row-main">
                <span className="fp-screen__row-title fp-type-title-medium">
                  <span className="fp-screen__row-figure">{airport.icao}</span> {airport.name}
                </span>
                <span className="fp-screen__row-detail fp-type-label-small">
                  {airport.location} · {airport.longestRunway}
                </span>
              </div>
              {airport.rules != null && <FlightRulesBadge rules={airport.rules} />}
            </button>
          ))}
        </div>
      </div>
    </PhoneFrame>
  )
}
