import { NavigationBar, PhoneFrame } from '../components/AppChrome'
import { NavigationRail } from '../components/NavigationRail'
import { TabletFrame } from '../components/TabletFrame'
import type { ScreenLayout } from './PlanScreen'
import { ModeSelector } from '../components/ModeSelector'
import {
  HeroDistanceCard,
  MetricGrid,
  MonthlyActivityCard,
  RankedListCard,
  VisitedNetworkCard,
  type VisitedAirport,
  type VisitedLeg,
} from '../components/StatsCards'

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
  /**
   * Every airport visited, with coordinates, for the network map.
   *
   * Omitted, the map is left out rather than drawn empty — a world map with
   * nothing on it says less than no map at all.
   */
  visited?: VisitedAirport[]
  /** The legs flown, drawn as great-circle arcs under the markers. */
  visitedLegs?: VisitedLeg[]
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
 *
 * The screen is a composition of the statistics cards rather than markup of its
 * own, so a card redesigned in a concept lands here and in any other arrangement
 * of these figures at once.
 */
export function StatsScreen({
  totalDistance,
  earthCircumferences,
  metrics,
  monthly,
  topAircraft,
  topAirports,
  visited,
  visitedLegs,
  selectedRange = 0,
  layout = 'phone',
  className,
}: StatsScreenProps) {
  const body = (
    <div className={layout === 'tablet' ? 'fp-screen fp-content-cap fp-content-cap--wide' : 'fp-screen'}>
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
        <HeroDistanceCard
          totalDistance={totalDistance}
          earthCircumferences={earthCircumferences}
        />

        <MetricGrid metrics={metrics} />

        {visited != null && visited.length > 0 && (
          <VisitedNetworkCard airports={visited} legs={visitedLegs} />
        )}

        <MonthlyActivityCard months={monthly} />

        <RankedListCard
          title="Most flown"
          rows={topAircraft.map((a) => ({ name: a.name, count: a.flights }))}
        />

        <RankedListCard
          title="Most visited"
          rows={topAirports.map((a) => ({ name: a.name, code: a.icao, count: a.visits }))}
        />
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
