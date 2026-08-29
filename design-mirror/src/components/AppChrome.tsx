import type { ReactNode } from 'react'

/**
 * The navigation-bar entries, in bar order.
 *
 * Settings is deliberately not one of them: the other four are places the content
 * lives and the user moves between constantly, while Settings is somewhere you go
 * once and come back from. It lives in the app bar instead, reachable from every
 * screen rather than taking a fifth of the bar.
 */
export type NavDestination = 'plan' | 'fleet' | 'logbook' | 'stats'

/**
 * The app's own icons, as the exact vector paths shipped in its drawables.
 *
 * Not stand-ins: a paper plane for Plan and an airframe for Fleet say more than
 * two generic glyphs would, and they are the glyphs the app actually draws. There
 * is one icon per entry rather than a filled/outlined pair, because Material
 * signals the current destination with the active indicator behind the icon and a
 * second glyph would restate what the indicator already says.
 */
export const navIconPaths: Record<NavDestination | 'settings', string> = {
  plan: 'M2.01,21L23,12 2.01,3 2,10l15,2 -15,2z',
  fleet:
    'M21,16v-2l-8,-5V3.5C13,2.67 12.33,2 11.5,2S10,2.67 10,3.5V9l-8,5v2l8,-2.5V19l-2,1.5V22l3.5,-1 3.5,1v-1.5L13,19v-5.5L21,16z',
  logbook: 'M3,13h2v-2H3v2zM3,17h2v-2H3v2zM3,9h2V7H3v2zM7,13h14v-2H7v2zM7,17h14v-2H7v2zM7,7v2h14V7H7z',
  stats: 'M5,9.2h3V19H5V9.2zM10.6,5h2.8v14h-2.8V5zM16.2,13H19v6h-2.8V13z',
  settings:
    'M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z',
}

const navLabels: Record<NavDestination, string> = {
  plan: 'Plan',
  fleet: 'Fleet',
  logbook: 'Logbook',
  stats: 'Stats',
}

export interface NavIconProps {
  name: NavDestination | 'settings'
  size?: number
  className?: string
}

/** One of the app's five icons, at any size. */
export function NavIcon({ name, size = 24, className }: NavIconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
      className={className}
    >
      <path d={navIconPaths[name]} />
    </svg>
  )
}

export interface NavigationBarProps {
  selected: NavDestination
  onSelect?: (destination: NavDestination) => void
  className?: string
}

/**
 * The bottom navigation bar: Plan, Fleet, Logbook, Stats.
 *
 * It floats over content that scrolls beneath it — the app is edge to edge and the
 * gesture area below this bar is genuinely empty, with nothing painted behind it.
 */
export function NavigationBar({ selected, onSelect, className }: NavigationBarProps) {
  return (
    <nav className={['fp-nav-bar', className].filter(Boolean).join(' ')}>
      {(Object.keys(navLabels) as NavDestination[]).map((destination) => {
        const active = destination === selected
        return (
          <button
            key={destination}
            type="button"
            className="fp-nav-bar__item"
            aria-current={active ? 'page' : undefined}
            onClick={() => onSelect?.(destination)}
          >
            <span
              className={[
                'fp-nav-bar__indicator',
                active ? 'fp-nav-bar__indicator--active' : null,
              ]
                .filter(Boolean)
                .join(' ')}
            >
              <NavIcon name={destination} />
            </span>
            <span className="fp-nav-bar__label fp-type-label-medium">{navLabels[destination]}</span>
          </button>
        )
      })}
    </nav>
  )
}

export interface TopAppBarProps {
  title: string
  /** Drawn on the right. Settings lives here rather than in the navigation bar. */
  action?: ReactNode
  /** Draws a back arrow on the left. */
  onBack?: () => void
  className?: string
}

/** The screen's app bar. Transparent, so content scrolls up under it. */
export function TopAppBar({ title, action, onBack, className }: TopAppBarProps) {
  return (
    <header className={['fp-app-bar', className].filter(Boolean).join(' ')}>
      {onBack != null && (
        <button type="button" className="fp-app-bar__back" onClick={onBack} aria-label="Back">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20V11z" />
          </svg>
        </button>
      )}
      <h1 className="fp-app-bar__title fp-type-headline-medium">{title}</h1>
      {action}
    </header>
  )
}

export interface PhoneFrameProps {
  /** The clock shown in the status bar. Defaults to `8:04`. */
  time?: string
  /**
   * Pinned above the gesture area rather than scrolling with the content — this
   * is where a {@link NavigationBar} goes.
   */
  bottomBar?: ReactNode
  children?: ReactNode
  className?: string
}

/**
 * A 360 × 800 phone, drawn the way this app actually occupies one.
 *
 * **The system bars are empty.** The window is edge to edge and the status and
 * navigation bars are transparent — nothing is painted behind them, so content
 * scrolls up under the clock and down under the gesture handle. A scrim, however
 * subtle, reads on a device as an opaque bar the moment a card is behind it; both
 * a scrim and an inset-padded container were built and removed for that reason.
 * A concept that paints a bar here is a concept that cannot be built.
 */
export function PhoneFrame({ time = '8:04', bottomBar, children, className }: PhoneFrameProps) {
  return (
    <div className={['fp-phone-frame', className].filter(Boolean).join(' ')}>
      <div className="fp-phone-frame__status">
        <span>{time}</span>
        <span>▪ ▪ ▮</span>
      </div>
      <div className="fp-phone-frame__content">{children}</div>
      {bottomBar}
      <div className="fp-phone-frame__gesture">
        <span className="fp-phone-frame__handle" />
      </div>
    </div>
  )
}
