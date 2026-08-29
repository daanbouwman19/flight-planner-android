import type { ReactNode } from 'react'
import { NavigationBar, PhoneFrame } from '../components/AppChrome'
import { NavigationRail } from '../components/NavigationRail'
import { TabletFrame, TwoPaneScaffold } from '../components/TabletFrame'
import { MonthHeader } from '../components/MonthHeader'
import { StatSummaryStrip, type StatTile } from '../components/StatSummaryStrip'
import { ValueChip } from '../components/ValueChip'
import type { ScreenLayout } from './PlanScreen'

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

export interface FlightDetailPaneProps {
  flight: LoggedFlight
  className?: string
}

/** One logged flight's detail, without a frame — the trailing pane on a tablet. */
export function FlightDetailPane({ flight, className }: FlightDetailPaneProps) {
  return (
    <div className={['fp-screen', 'fp-content-cap', 'fp-content-cap--wide', className].filter(Boolean).join(' ')}>
      <div className="fp-screen__header">
        <h1 className="fp-screen__title fp-type-headline-small">
          {flight.departure} → {flight.destination}
        </h1>
      </div>
      <div className="fp-screen__list">
        <span className="fp-screen__row-detail fp-type-body-medium">
          {flight.date} · {flight.aircraft}
        </span>
        <div className="fp-detail-facts">
          <ValueChip label="DIST" value={flight.distance} />
          <ValueChip label="TIME" value={flight.duration} />
        </div>
      </div>
    </div>
  )
}

export interface LogbookScreenProps {
  months: LogbookMonth[]
  /** The three headline figures above the list. */
  summary?: StatTile[]
  /** Defaults to `phone`. */
  layout?: ScreenLayout
  /** Shown beside the list on `tablet`. Pass a {@link FlightDetailPane}. */
  detail?: ReactNode
  className?: string
}

/**
 * Logbook: flights already flown, newest first, grouped by month.
 *
 * The month headers are sticky and **opaque**, unlike the rest of the app's
 * chrome. That is not a violation of the transparent-bars rule: that rule is about
 * the window's system bars, and a sticky header inside a scrolling list has to stay
 * legible over whichever row is passing behind it.
 *
 * The logbook takes the **wider** content cap on a big window — its rows are less
 * dense than Plan's cards, so the extra width is readable rather than wasted.
 */
export function LogbookScreen({
  months,
  summary,
  layout = 'phone',
  detail,
  className,
}: LogbookScreenProps) {
  const list = (
    <div
      className={
        layout === 'tablet' ? 'fp-screen fp-content-cap fp-content-cap--wide' : 'fp-screen'
      }
    >
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
                  <div
                    className="fp-screen__row-main"
                    style={{ flex: 'none', alignItems: 'flex-end' }}
                  >
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
  )

  if (layout === 'tablet') {
    return (
      <TabletFrame className={className} rail={<NavigationRail selected="logbook" />}>
        {detail != null ? <TwoPaneScaffold list={list} detail={detail} /> : list}
      </TabletFrame>
    )
  }

  return (
    <PhoneFrame className={className} bottomBar={<NavigationBar selected="logbook" />}>
      {list}
    </PhoneFrame>
  )
}
