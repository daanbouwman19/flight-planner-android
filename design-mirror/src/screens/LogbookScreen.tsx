import { NavigationBar, PhoneFrame } from '../components/AppChrome'
import { MonthHeader } from '../components/MonthHeader'
import { StatSummaryStrip, type StatTile } from '../components/StatSummaryStrip'

export interface LoggedFlight {
  departure: string
  destination: string
  aircraft: string
  /** `12 Aug`. */
  date: string
  /** Already formatted — `1,308 NM`. */
  distance: string
  /** Already formatted — `2:47`. */
  duration: string
}

export interface LogbookMonth {
  /** `August 2026`. */
  label: string
  flights: LoggedFlight[]
}

export interface LogbookScreenProps {
  months: LogbookMonth[]
  /** The three headline figures above the list. */
  summary?: StatTile[]
  className?: string
}

/**
 * Logbook: flights already flown, newest first, grouped by month.
 *
 * The month headers are sticky and **opaque**, unlike the rest of the app's
 * chrome. That is not a violation of the transparent-bars rule: that rule is about
 * the window's system bars, and a sticky header inside a scrolling list has to stay
 * legible over whichever row is passing behind it.
 */
export function LogbookScreen({ months, summary, className }: LogbookScreenProps) {
  return (
    <PhoneFrame className={className} bottomBar={<NavigationBar selected="logbook" />}>
      <div className="fp-screen">
        <div className="fp-screen__header">
          <h1 className="fp-screen__title fp-type-headline-medium">Logbook</h1>
        </div>

        {summary != null && (
          <div className="fp-screen__controls">
            <StatSummaryStrip tiles={summary} />
          </div>
        )}

        <div className="fp-screen__list">
          {months.map((month) => (
            <div key={month.label}>
              <MonthHeader label={month.label} />
              <div className="fp-screen__list">
                {month.flights.map((flight, i) => (
                  <button
                    type="button"
                    className="fp-screen__row"
                    key={`${flight.departure}-${flight.destination}-${i}`}
                  >
                    <div className="fp-screen__row-main">
                      <span className="fp-screen__row-title fp-type-title-medium">
                        {flight.departure} → {flight.destination}
                      </span>
                      <span className="fp-screen__row-detail fp-type-label-small">
                        {flight.date} · {flight.aircraft}
                      </span>
                    </div>
                    <div className="fp-screen__row-main" style={{ flex: 'none', alignItems: 'flex-end' }}>
                      <span className="fp-screen__row-figure fp-type-label-large">
                        {flight.distance}
                      </span>
                      <span className="fp-screen__row-detail fp-type-label-small">
                        {flight.duration}
                      </span>
                    </div>
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </PhoneFrame>
  )
}
