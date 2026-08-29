import type { CSSProperties, ReactNode } from 'react'
import { createContext, useContext, useEffect, useState } from 'react'
import { tokens } from '../tokens/tokens.gen'

/**
 * The five looks the app offers, mirroring `ThemeChoice` in Kotlin.
 *
 * `system` follows the viewer's `prefers-color-scheme` and resolves to `brandLight`
 * or `brandDark`. `cockpit` and `chart` are **not** a third dark mode and a second
 * light one — Cockpit is a near-black instrument panel meant for flying at night,
 * Chart is printed chart paper. In the app both deliberately ignore dynamic colour,
 * because each one's identity is a specific pair of surfaces and ink.
 */
export type ThemeChoice = 'system' | 'brandLight' | 'brandDark' | 'cockpit' | 'chart'

/** The resolved scheme — `system` has been decided one way or the other. */
export type ResolvedTheme = 'brandLight' | 'brandDark' | 'cockpit' | 'chart'

const ThemeContext = createContext<ResolvedTheme>('brandLight')

/** The scheme in scope. Components that must branch on tone read this. */
export function useFlightTheme(): ResolvedTheme {
  return useContext(ThemeContext)
}

/**
 * Whether the viewer's system is in dark mode.
 *
 * Read only to answer {@link useFlightTheme} and {@link useIsDark} for a `system`
 * theme — never to choose a colour, which the CSS does on its own. It starts at
 * `false` and corrects in an effect, so a component that branches on `useIsDark`
 * may render its light branch for one frame; the painted scheme is right from the
 * first, because it never came from here.
 */
function useSystemDark(): boolean {
  const [dark, setDark] = useState(false)
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return
    const query = window.matchMedia('(prefers-color-scheme: dark)')
    setDark(query.matches)
    const onChange = (e: MediaQueryListEvent) => setDark(e.matches)
    query.addEventListener('change', onChange)
    return () => query.removeEventListener('change', onChange)
  }, [])
  return dark
}

/** True when the scheme in scope is a dark one — Cockpit counts. */
export function useIsDark(): boolean {
  const theme = useFlightTheme()
  return theme === 'brandDark' || theme === 'cockpit'
}

export interface FlightPlannerThemeProps {
  /** Which of the five looks to wear. Defaults to `system`. */
  theme?: ThemeChoice
  /** Fills the viewport and paints the scheme's background. Defaults to `false`. */
  fullBleed?: boolean
  className?: string
  style?: CSSProperties
  children?: ReactNode
}

/**
 * **Wrap every design in this.** It is where the design system's colours live.
 *
 * Each scheme is a block of CSS custom properties keyed on `data-fp-theme`, so a
 * component outside this wrapper resolves `var(--fp-primary)` against nothing and
 * renders unstyled — the single most common way to get a broken-looking design out
 * of this library. Nesting is fine and is how a design shows two themes side by
 * side; the inner wrapper wins for its own subtree.
 *
 * ```tsx
 * <FlightPlannerTheme theme="brandDark" fullBleed>
 *   <RouteCard from="EHAM" to="KJFK" distanceNm={3312} rules="VFR" />
 * </FlightPlannerTheme>
 * ```
 */
export function FlightPlannerTheme({
  theme = 'system',
  fullBleed = false,
  className,
  style,
  children,
}: FlightPlannerThemeProps) {
  // `system` is resolved **in CSS** rather than by reading matchMedia, so a
  // design renders correctly on the first paint in whichever mode the viewer is in
  // — there is no frame where the wrong scheme is showing. The colours never wait
  // on this hook.
  //
  // What the context carries is a separate question, and it used to answer it
  // wrongly: hard-coding `brandLight` made `useIsDark()` report light to a
  // component branching on it while the CSS around that component was already
  // dark. So the media query is read here too, once, for the hooks alone.
  const systemDark = useSystemDark()
  const resolved: ResolvedTheme = theme === 'system' ? (systemDark ? 'brandDark' : 'brandLight') : theme
  const attr = theme === 'system' ? 'system' : kebab(theme)

  return (
    <ThemeContext.Provider value={resolved}>
      <div
        data-fp-theme={attr}
        className={['fp-root', fullBleed ? 'fp-root--full-bleed' : null, className]
          .filter(Boolean)
          .join(' ')}
        style={style}
      >
        {children}
      </div>
    </ThemeContext.Provider>
  )
}

function kebab(s: string): string {
  return s.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase()
}

/** Every colour role in the scheme, for a design that needs to enumerate them. */
export const colorRoles = Object.keys(tokens.schemes.brandLight) as Array<
  keyof typeof tokens.schemes.brandLight
>
