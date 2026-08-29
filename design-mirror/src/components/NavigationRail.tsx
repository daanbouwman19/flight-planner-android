import { NavIcon, type NavDestination } from './AppChrome'

/**
 * The destinations a rail shows.
 *
 * **Settings is here and is not in the bottom bar.** The four content
 * destinations are places the user moves between constantly; Settings is
 * somewhere you go once and come back from, so on a phone it lives in the app bar
 * rather than taking a fifth of the bar. A rail has room, so it gets a permanent
 * home there instead — which is a real difference between the two layouts, not a
 * detail.
 */
export type RailDestination = NavDestination | 'settings'

const railLabels: Record<RailDestination, string> = {
  plan: 'Plan',
  fleet: 'Fleet',
  logbook: 'Logbook',
  stats: 'Stats',
  settings: 'Settings',
}

export interface NavigationRailProps {
  selected: RailDestination
  onSelect?: (destination: RailDestination) => void
  className?: string
}

/**
 * The vertical navigation a wide window gets in place of the bottom bar.
 *
 * **The rail never auto-hides.** The bar retracts as the user scrolls because a
 * horizontal bar is the only form that gives height back; a rail costs width, and
 * width is what a wide window has spare. Do not design a rail that scrolls away.
 *
 * ```tsx
 * <NavigationRail selected="fleet" />
 * ```
 */
export function NavigationRail({ selected, onSelect, className }: NavigationRailProps) {
  return (
    <nav className={['fp-nav-rail', className].filter(Boolean).join(' ')}>
      {(Object.keys(railLabels) as RailDestination[]).map((destination) => {
        const active = destination === selected
        return (
          <button
            key={destination}
            type="button"
            className="fp-nav-rail__item"
            aria-current={active ? 'page' : undefined}
            onClick={() => onSelect?.(destination)}
          >
            <span
              className={[
                'fp-nav-rail__indicator',
                active ? 'fp-nav-rail__indicator--active' : null,
              ]
                .filter(Boolean)
                .join(' ')}
            >
              <NavIcon name={destination} />
            </span>
            <span className="fp-nav-rail__label fp-type-label-medium">
              {railLabels[destination]}
            </span>
          </button>
        )
      })}
    </nav>
  )
}
