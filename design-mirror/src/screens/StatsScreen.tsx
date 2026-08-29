import { NavigationBar, PhoneFrame } from '../components/AppChrome'
import { NavigationRail } from '../components/NavigationRail'
import { TabletFrame } from '../components/TabletFrame'
import type { ScreenLayout } from './PlanScreen'
import { ModeSelector } from '../components/ModeSelector'

export interface StatsScreenProps {
  /** Already formatted — `48,213 NM`. */
  totalDistance: string
  /** `1.2 × around the Earth` — the figure made graspable. */
  earthCircumferences: string
  /** Four labelled figures under the hero. */
  metrics: Array<{ label: string; value: string }>
  /** Flights per month, most recent last. Values are counts. */
  monthly: Array<{ label: string; value: number }>
  /** The airframes flown most, with their share. */
  topAircraft: Array<{ name: string; flights: number }>
  /** Airports visited most. */
  topAirports: Array<{ icao: string; name: string; visits: number }>
  selectedRange?: number
  /** Defaults to `phone`. */
  layout?: ScreenLayout
  className?: string
}

/**
 * Stats: what the logbook adds up to.
 *
 * The hero figure counts up once when it appears or changes — the motion
 * principle's own example of an animation worth having, because it draws the eye
 * to a value that changed. Every figure here is set in tabular figures so a column
 * of them aligns and a counting value does not make the layout twitch.
 */
export function StatsScreen({
  totalDistance,
  earthCircumferences,
  metrics,
  monthly,
  topAircraft,
  topAirports,
  selectedRange = 0,
  layout = 'phone',
  className,
}: StatsScreenProps) {
  const peak = Math.max(1, ...monthly.map((m) => m.value))
  const body = (
    <div className={layout === 'tablet' ? 'fp-screen fp-content-cap' : 'fp-screen'}>
        <div className="fp-screen__header">
          <h1 className="fp-screen__title fp-type-headline-medium">Stats</h1>
        </div>

        <div className="fp-screen__controls">
          <ModeSelector
            options={[{ label: 'All time' }, { label: 'This year' }, { label: '90 days' }]}
            selectedIndex={selectedRange}
          />
        </div>

        <div className="fp-screen__list">
          <div className="fp-stats-hero">
            <span className="fp-stats-hero__label fp-type-label-small">TOTAL DISTANCE</span>
            <span className="fp-stats-hero__value fp-type-headline-large">{totalDistance}</span>
            <span className="fp-stats-hero__pill fp-type-label-small">{earthCircumferences}</span>
          </div>

          <div className="fp-stats-metrics">
            {metrics.map((m) => (
              <div className="fp-stats-metric" key={m.label}>
                <span className="fp-stats-metric__value fp-type-title-large">{m.value}</span>
                <span className="fp-stats-metric__label fp-type-label-small">{m.label}</span>
              </div>
            ))}
          </div>

          <div className="fp-screen__card">
            <span className="fp-screen__card-title fp-type-label-large">Flights by month</span>
            <div className="fp-stats-bars">
              {monthly.map((m) => (
                <div className="fp-stats-bars__column" key={m.label}>
                  <div
                    className="fp-stats-bars__bar"
                    style={{ height: `${Math.round((m.value / peak) * 100)}%` }}
                    title={`${m.label}: ${m.value}`}
                  />
                  <span className="fp-stats-bars__label fp-type-label-small">{m.label}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="fp-screen__card">
            <span className="fp-screen__card-title fp-type-label-large">Most flown</span>
            {topAircraft.map((a) => (
              <div className="fp-stats-line" key={a.name}>
                <span className="fp-stats-line__name fp-type-body-medium">{a.name}</span>
                <span className="fp-screen__row-figure fp-type-label-large">{a.flights}</span>
              </div>
            ))}
          </div>

          <div className="fp-screen__card">
            <span className="fp-screen__card-title fp-type-label-large">Most visited</span>
            {topAirports.map((a) => (
              <div className="fp-stats-line" key={a.icao}>
                <span className="fp-stats-line__name fp-type-body-medium">
                  <strong>{a.icao}</strong> {a.name}
                </span>
                <span className="fp-screen__row-figure fp-type-label-large">{a.visits}</span>
              </div>
            ))}
          </div>
        </div>
    </div>
  )

  if (layout === 'tablet') {
    return (
      <TabletFrame className={className} rail={<NavigationRail selected="stats" />}>
        {body}
      </TabletFrame>
    )
  }

  return (
    <PhoneFrame className={className} bottomBar={<NavigationBar selected="stats" />}>
      {body}
    </PhoneFrame>
  )
}
