import type { ReactNode } from 'react'
import { NavigationBar, PhoneFrame } from '../components/AppChrome'
import { NavigationRail } from '../components/NavigationRail'
import { TabletFrame, TwoPaneScaffold } from '../components/TabletFrame'
import { ModeSelector } from '../components/ModeSelector'
import { ValueChip } from '../components/ValueChip'
import type { ScreenLayout } from './PlanScreen'

export interface Aircraft {
  /** `Cessna 172S Skyhawk`. */
  variant: string
  /** `Single Engine Piston`. */
  category: string
  /** Already formatted — `640 NM`. */
  range: string
  /** Already formatted — `1,685 ft`. */
  requiredRunway: string
  /** How many logged flights this airframe has. */
  flights?: number
}

export interface FleetDetailPaneProps {
  aircraft: Aircraft
  /** Already formatted — `124 kt`. */
  cruiseSpeed?: string
  className?: string
}

/**
 * One airframe's detail, without a frame around it.
 *
 * The envelope is stated as editable figures rather than as prose, because those
 * two numbers are what every generated route is constrained by — a fleet entry
 * whose range the reader cannot see is one they cannot predict Plan's output from.
 */
export function FleetDetailPane({ aircraft, cruiseSpeed, className }: FleetDetailPaneProps) {
  return (
    <div className={['fp-screen', 'fp-content-cap', className].filter(Boolean).join(' ')}>
      <div className="fp-screen__header">
        <h1 className="fp-screen__title fp-type-headline-small">{aircraft.variant}</h1>
      </div>
      <div className="fp-screen__list">
        <span className="fp-screen__row-detail fp-type-body-medium">{aircraft.category}</span>
        <div className="fp-detail-facts">
          <ValueChip label="RANGE" value={aircraft.range} />
          <ValueChip label="RWY" value={aircraft.requiredRunway} />
          {cruiseSpeed != null && <ValueChip label="CRUISE" value={cruiseSpeed} />}
          {aircraft.flights != null && (
            <ValueChip label="FLOWN" value={String(aircraft.flights)} />
          )}
        </div>
        <button type="button" className="fp-button fp-type-label-large">
          Generate routes
        </button>
      </div>
    </div>
  )
}

export interface FleetScreenProps {
  aircraft: Aircraft[]
  /** Index into the category filter chips. */
  selectedCategory?: number
  categories?: string[]
  /** Defaults to `phone`. */
  layout?: ScreenLayout
  /** Shown beside the list on `tablet`. Pass a {@link FleetDetailPane}. */
  detail?: ReactNode
  /** Which row reads as selected, for the two-pane layout. */
  selectedVariant?: string
  className?: string
}

/**
 * Fleet: the airframes a route can be generated for.
 *
 * Each row states the **envelope** — range and required runway — because those two
 * numbers are what actually constrain a generated route, and a fleet list that only
 * named aircraft would leave the reader unable to predict what Plan will do.
 */
export function FleetScreen({
  aircraft,
  selectedCategory = 0,
  categories = ['All', 'Piston', 'Turboprop', 'Jet'],
  layout = 'phone',
  detail,
  selectedVariant,
  className,
}: FleetScreenProps) {
  const list = (
    <div className={layout === 'tablet' ? 'fp-screen fp-content-cap' : 'fp-screen'}>
      <div className="fp-screen__header">
        <h1 className="fp-screen__title fp-type-headline-medium">Fleet</h1>
      </div>

      <div className="fp-screen__controls">
        <ModeSelector
          options={categories.map((label) => ({ label }))}
          selectedIndex={selectedCategory}
        />
      </div>

      <div className="fp-screen__list">
        {aircraft.map((a) => (
          <button
            type="button"
            className={[
              'fp-screen__row',
              a.variant === selectedVariant ? 'fp-screen__row--selected' : null,
            ]
              .filter(Boolean)
              .join(' ')}
            key={a.variant}
            aria-current={a.variant === selectedVariant ? 'true' : undefined}
          >
            <div className="fp-screen__row-main">
              <span className="fp-screen__row-title fp-type-title-medium">{a.variant}</span>
              <span className="fp-screen__row-detail fp-type-label-small">
                {a.category} · {a.range} · {a.requiredRunway}
              </span>
            </div>
            {a.flights != null && (
              <span className="fp-screen__row-figure fp-type-label-large">{a.flights}</span>
            )}
          </button>
        ))}
      </div>
    </div>
  )

  if (layout === 'tablet') {
    return (
      <TabletFrame className={className} rail={<NavigationRail selected="fleet" />}>
        {detail != null ? <TwoPaneScaffold list={list} detail={detail} /> : list}
      </TabletFrame>
    )
  }

  return (
    <PhoneFrame className={className} bottomBar={<NavigationBar selected="fleet" />}>
      {list}
    </PhoneFrame>
  )
}
