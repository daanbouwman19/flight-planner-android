import { NavIcon, NavigationBar, PhoneFrame } from '../components/AppChrome'
import { FilterField } from '../components/FilterField'
import { ModeSelector } from '../components/ModeSelector'
import { RouteCard, type RouteCardProps } from '../components/RouteCard'

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
  className?: string
}

/**
 * Plan: the screen the app opens on, and the one the user actually looks at.
 *
 * The header is a title, two actions and the controls; everything below is a list
 * of route cards. **The controls scroll with the list rather than pinning** — they
 * are set once and then read, so holding a fifth of a phone's height for them
 * permanently would spend the screen on the wrong thing.
 *
 * Content runs under the status bar and under the gesture area. Nothing is painted
 * behind either.
 *
 * ```tsx
 * <PlanScreen routes={[...]} notFlownCount={116} selectedMode={1} />
 * ```
 */
export function PlanScreen({
  routes,
  selectedMode = 0,
  notFlownCount = 0,
  departure,
  aircraft,
  className,
}: PlanScreenProps) {
  return (
    <PhoneFrame className={className} bottomBar={<NavigationBar selected="plan" />}>
      <div className="fp-screen">
        <div className="fp-screen__header">
          <h1 className="fp-screen__title fp-type-headline-medium">Plan</h1>
          <button type="button" className="fp-app-bar__back" aria-label="Browse airports">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z" />
            </svg>
          </button>
          <button type="button" className="fp-app-bar__back" aria-label="Settings">
            <NavIcon name="settings" />
          </button>
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
          {/* A pair of equal-height fields: the detail line is the point, saying
              what the filter *means* rather than only what it is set to. */}
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
            <RouteCard key={`${route.departure.icao}-${route.destination.icao}-${i}`} {...route} />
          ))}
        </div>
      </div>
    </PhoneFrame>
  )
}
