import type { ReactNode } from 'react'
import { NavIcon, NavigationBar, PhoneFrame } from '../components/AppChrome'
import { NavigationRail } from '../components/NavigationRail'
import { TabletFrame, TwoPaneScaffold } from '../components/TabletFrame'
import { FilterField } from '../components/FilterField'
import { ModeSelector } from '../components/ModeSelector'
import { RouteCard, type RouteCardProps } from '../components/RouteCard'

/**
 * Which window the screen is being drawn for.
 *
 * The app has **one** Plan screen that adapts, not two screens — so this is a
 * width, not a variant. `phone` is a 360 × 800 window with a bottom bar; `tablet`
 * is 1280 × 800 with a rail and, when a route is selected, a detail pane beside
 * the list.
 */
export type ScreenLayout = 'phone' | 'tablet'

export interface PlanScreenProps {
  /** The generated routes, newest first. */
  routes: Array<Omit<RouteCardProps, 'className'>>
  /** Index into the three modes: All, Not flown, This aircraft. */
  selectedMode?: number
  /** Drawn on the "Not flown" chip. */
  notFlownCount?: number
  /** The locked departure, or `undefined` for "Any". */
  departure?: { icao: string; name: string }
  /** The chosen aircraft, or `undefined` for "Any". */
  aircraft?: { variant: string; envelope: string }
  /** Defaults to `phone`. */
  layout?: ScreenLayout
  /**
   * The detail pane's content, shown beside the list on `tablet`.
   *
   * Ignored on `phone`, where the detail is a separate screen the list navigates
   * to. Pass a `RouteDetailPane`.
   */
  detail?: ReactNode
  className?: string
}

/**
 * Plan: the screen the app opens on, and the one the user actually looks at.
 *
 * A title, two actions and the controls; everything below is a list of route
 * cards. **The controls scroll with the list rather than pinning** — they are set
 * once and then read, so holding a fifth of a phone's height for them permanently
 * would spend the screen on the wrong thing.
 *
 * On a wide window it becomes a list/detail pair and the bottom bar becomes a rail
 * — and the content is **width-capped rather than stretched**, because past about
 * 640px a single column stops helping.
 *
 * ```tsx
 * <PlanScreen routes={routes} notFlownCount={116} />
 * <PlanScreen routes={routes} layout="tablet" detail={<RouteDetailPane … />} />
 * ```
 */
export function PlanScreen({
  routes,
  selectedMode = 0,
  notFlownCount = 0,
  departure,
  aircraft,
  layout = 'phone',
  detail,
  className,
}: PlanScreenProps) {
  const list = (
    <div className={layout === 'tablet' ? 'fp-screen fp-content-cap' : 'fp-screen'}>
      <div className="fp-screen__header">
        <h1 className="fp-screen__title fp-type-headline-medium">Plan</h1>
        <button type="button" className="fp-app-bar__back" aria-label="Browse airports">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z" />
          </svg>
        </button>
        {/* On a tablet the rail carries Settings, so the header action would be a
            second way to the same place. */}
        {layout === 'phone' && (
          <button type="button" className="fp-app-bar__back" aria-label="Settings">
            <NavIcon name="settings" />
          </button>
        )}
      </div>

      <div className="fp-screen__controls">
        <ModeSelector
          options={[
            { label: 'All' },
            { label: 'Not flown', count: notFlownCount, enabled: notFlownCount > 0 },
            { label: 'This aircraft' },
          ]}
          selectedIndex={selectedMode}
        />
        {/* A pair of equal-height fields: the detail line is the point, saying what
            the filter *means* rather than only what it is set to. */}
        <div className="fp-screen__filters">
          <FilterField
            label="DEPARTURE"
            value={departure?.icao ?? 'Any'}
            detail={departure?.name}
            selected={departure != null}
          />
          <FilterField
            label="AIRCRAFT"
            value={aircraft?.variant ?? 'Any'}
            detail={aircraft?.envelope}
            selected={aircraft != null}
          />
        </div>
      </div>

      <div className="fp-screen__list">
        {routes.map((route, i) => (
          <RouteCard
            key={`${route.departure.icao}-${route.destination.icao}-${i}`}
            enterIndex={i}
            {...route}
          />
        ))}
      </div>
    </div>
  )

  if (layout === 'tablet') {
    return (
      <TabletFrame className={className} rail={<NavigationRail selected="plan" />}>
        {detail != null ? <TwoPaneScaffold list={list} detail={detail} /> : list}
      </TabletFrame>
    )
  }

  return (
    <PhoneFrame className={className} bottomBar={<NavigationBar selected="plan" />}>
      {list}
    </PhoneFrame>
  )
}
