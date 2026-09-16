import { useId, useMemo } from 'react'
import { ValueChip } from './ValueChip'

export interface GlobeCameraControlsProps {
  /** The camera's heading, degrees. 0 is north (the reset control is hidden). */
  bearingDegrees?: number
  className?: string
}

/**
 * The camera instrument the real globe draws over live imagery — zoom in,
 * zoom out, refit, and, once the view has turned, a heading cell with a
 * needle and the bearing as a figure.
 *
 * Ported from `GlobeCameraControls` in `:feature:globe`, at the values named
 * there: a 44 dp cell on a `surfaceContainer` plate at 82 % alpha, an
 * `extraSmall` corner, one hairline of `outlineVariant` between cells. The
 * zoom marks are drawn strokes rather than icons — a plus and a minus at the
 * app's own hairline weight — for the same reason the real component gives:
 * nothing in a borrowed icon says more than a cross does.
 */
export function GlobeCameraControls({ bearingDegrees = 0, className }: GlobeCameraControlsProps) {
  const rotated = Math.abs(((bearingDegrees % 360) + 360) % 360) > 0.5
  const heading = Math.round(((bearingDegrees % 360) + 360) % 360)
  return (
    <div className={['fp-globe-plate', 'fp-globe-plate--controls', className].filter(Boolean).join(' ')}>
      <div className="fp-globe-cell" aria-hidden="true">
        <svg viewBox="0 0 18 18" width={18} height={18}>
          <path d="M9 3v12M3 9h12" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" />
        </svg>
      </div>
      <div className="fp-globe-rule" />
      <div className="fp-globe-cell" aria-hidden="true">
        <svg viewBox="0 0 18 18" width={18} height={18}>
          <path d="M3 9h12" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" />
        </svg>
      </div>
      <div className="fp-globe-rule" />
      <div className="fp-globe-cell" aria-hidden="true">
        <svg viewBox="0 0 18 18" width={18} height={18}>
          <path
            d="M2 6V2h4M16 6V2h-4M2 12v4h4M16 12v4h-4"
            fill="none"
            stroke="currentColor"
            strokeWidth={1.5}
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </div>
      {rotated && (
        <>
          <div className="fp-globe-rule" />
          <div className="fp-globe-cell fp-globe-cell--heading">
            <svg
              viewBox="0 0 16 16"
              width={16}
              height={16}
              style={{ transform: `rotate(${-bearingDegrees}deg)` }}
            >
              <path d="M8 1 L11 8 L8 15 L5 8 Z" fill="none" stroke="var(--fp-outline-variant)" strokeWidth={1.2} />
              <path d="M8 1 L11 8 L8 8 Z" fill="var(--fp-on-surface)" />
            </svg>
            <span className="fp-type-label-small fp-globe-heading__figure">{heading}°</span>
          </div>
        </>
      )}
    </div>
  )
}

export interface GlobeAttributionProps {
  /** Defaults to the keyless credit — NASA GIBS — the same one a clone with no
   * ArcGIS key in `local.properties` shows, and the one goldens are pinned to. */
  label?: string
  credit?: string
  className?: string
}

/** The imagery credit every layout over the globe draws. Ported from `GlobeAttribution`. */
export function GlobeAttribution({ label = 'Imagery: NASA GIBS', credit, className }: GlobeAttributionProps) {
  return (
    <div className={['fp-globe-plate', 'fp-globe-plate--credit', className].filter(Boolean).join(' ')} aria-hidden="true">
      <span className="fp-type-label-small">{label}</span>
      {credit != null && <span className="fp-type-label-small fp-globe-credit__line">{credit}</span>}
    </div>
  )
}

export interface GlobeSphereProps {
  /** Where the sun sits over the disc, 0 = centred (noon side toward the viewer), 1 = grazing the limb (dawn/dusk). */
  terminator?: number
  className?: string
}

/**
 * The planet itself — a deliberate stand-in, not a port.
 *
 * `:feature:globe` renders this as a Filament/Vulkan scene: a tessellated
 * mesh, quadtree-streamed satellite tiles, a hand-written camera and gesture
 * system, roughly ten thousand lines on the native side. Reproducing that
 * pipeline in WebGL for a design canvas is a project of its own and out of
 * scope for this mirror — see `.design-sync/NOTES.md`. What is reproduced
 * here is the *reading* of the hero: a lit sphere with a limb and a
 * terminator, themed through the same six roles `rememberGlobeInk` resolves
 * (`GlobeInkTheme.kt`) rather than a literal colour, so Chart gets a paper
 * planet and Cockpit a dim one the same way the real globe does.
 */
export function GlobeSphere({ terminator = 0.35, className }: GlobeSphereProps) {
  const gradId = useId()
  const termId = useId()
  return (
    <svg
      className={['fp-globe-sphere', className].filter(Boolean).join(' ')}
      viewBox="0 0 200 200"
      preserveAspectRatio="xMidYMid slice"
      aria-hidden="true"
    >
      <defs>
        <radialGradient id={gradId} cx="38%" cy="38%" r="75%">
          <stop offset="0%" stopColor="var(--fp-primary)" stopOpacity={0.55} />
          <stop offset="55%" stopColor="var(--fp-primary)" stopOpacity={0.3} />
          <stop offset="88%" stopColor="var(--fp-outline)" stopOpacity={0.35} />
          <stop offset="100%" stopColor="var(--fp-outline)" stopOpacity={0.7} />
        </radialGradient>
        <linearGradient id={termId} x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="var(--fp-surface)" stopOpacity={0} />
          <stop offset={`${Math.round((1 - terminator) * 100)}%`} stopColor="var(--fp-surface)" stopOpacity={0} />
          <stop offset="100%" stopColor="var(--fp-scrim)" stopOpacity={0.5} />
        </linearGradient>
      </defs>
      <circle cx="100" cy="100" r="96" fill={`url(#${gradId})`} />
      {/* A few translucent land masses — layer order and weight, not
          geography, the same disclaimer RouteMap's own preview island carries. */}
      <g fill="var(--fp-on-surface)" opacity={0.12}>
        <ellipse cx="72" cy="82" rx="34" ry="22" />
        <ellipse cx="128" cy="120" rx="26" ry="16" />
        <ellipse cx="95" cy="145" rx="18" ry="10" />
      </g>
      <circle cx="100" cy="100" r="96" fill={`url(#${termId})`} />
      {/* The limb: a hairline where the disc meets space. */}
      <circle cx="100" cy="100" r="96" fill="none" stroke="var(--fp-outline)" strokeWidth={1} strokeOpacity={0.6} />
    </svg>
  )
}

export interface GlobeRouteArcProps {
  className?: string
}

/**
 * The leg, bent over the sphere's curve rather than drawn straight across it —
 * the one piece of `RouteMap`'s ink this stand-in borrows outright: cased
 * primary line, hollow departure, filled destination.
 */
export function GlobeRouteArc({ className }: GlobeRouteArcProps) {
  const path = 'M 44,132 Q 100,60 156,96'
  return (
    <svg
      className={['fp-globe-route', className].filter(Boolean).join(' ')}
      viewBox="0 0 200 200"
      preserveAspectRatio="xMidYMid slice"
      aria-hidden="true"
    >
      <path d={path} fill="none" stroke="var(--fp-surface-container)" strokeWidth={5.5} strokeLinecap="round" />
      <path d={path} fill="none" stroke="var(--fp-primary)" strokeWidth={2.5} strokeLinecap="round" />
      <circle cx={44} cy={132} r={4} fill="none" stroke="var(--fp-primary)" strokeWidth={2} />
      <circle cx={156} cy={96} r={4} fill="var(--fp-primary)" />
    </svg>
  )
}

export interface GlobeHeroProps {
  /**
   * The hero's height in px. The real screen sizes this to 44 % of the
   * window (`heroHeight()` in `RouteGlobeHero.kt`) so the sphere has room to
   * read as one; a static preview has no window to be a fraction of, so a
   * caller passes the figure directly.
   */
  height?: number
  bearingDegrees?: number
  className?: string
}

/**
 * The deep hero: the globe claiming the top of the route detail screen.
 *
 * Stands in for `DeepGlobeHero` (`app/.../ui/detail/RouteGlobeHero.kt`). DIST
 * and BRG are deliberately **not** drawn as chips on the glass — the real
 * screen moved them onto its own figure spine and this hero carries none, a
 * divergence recorded in `RouteGlobeHero.kt`'s own KDoc and in NOTES.md here.
 */
export function GlobeHero({ height = 280, bearingDegrees = 0, className }: GlobeHeroProps) {
  const scene = useMemo(() => ({ bearingDegrees }), [bearingDegrees])
  return (
    <div className={['fp-globe-hero', className].filter(Boolean).join(' ')} style={{ height }}>
      <GlobeSphere />
      <GlobeRouteArc />
      <GlobeCameraControls bearingDegrees={scene.bearingDegrees} className="fp-globe-hero__controls" />
      <GlobeAttribution className="fp-globe-hero__credit" />
    </div>
  )
}

export interface ImmersiveGlobeViewProps {
  departureIcao: string
  destinationIcao: string
  aircraft: string
  /** Already formatted — `3,153 NM`. */
  distance: string
  /** Already formatted — `271°`. */
  bearing?: string
  /** Already formatted — `7:04`. */
  flightTime?: string
  bearingDegrees?: number
  height?: number
  className?: string
}

/**
 * The globe with the window to itself — stands in for `ImmersiveGlobeScreen`.
 *
 * Unlike the deep hero, this carries the whole route as one plate of figures
 * over the sphere — the pair, the airframe, DIST/BRG/TIME — because here
 * there is no spine of figures elsewhere on the screen to state them. Same
 * rule as the hero, *"the figures the arc is about sit next to the arc"*,
 * reaching a different answer because the arc is the whole window.
 */
export function ImmersiveGlobeView({
  departureIcao,
  destinationIcao,
  aircraft,
  distance,
  bearing,
  flightTime,
  bearingDegrees = 0,
  height = 480,
  className,
}: ImmersiveGlobeViewProps) {
  return (
    <div className={['fp-globe-immersive', className].filter(Boolean).join(' ')} style={{ height }}>
      <GlobeSphere />
      <GlobeRouteArc />
      <div className="fp-globe-immersive__collapse" aria-hidden="true">
        <svg viewBox="0 0 18 18" width={18} height={18}>
          <path
            d="M10 3v5h5M8 15v-5H3"
            fill="none"
            stroke="currentColor"
            strokeWidth={1.5}
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </div>
      <GlobeCameraControls bearingDegrees={bearingDegrees} className="fp-globe-immersive__controls" />
      <div className="fp-globe-immersive__foot">
        <GlobeAttribution className="fp-globe-immersive__credit" />
        <div className="fp-globe-route-plate">
          <div className="fp-globe-route-plate__row">
            <span className="fp-globe-route-plate__pair fp-type-title-small">
              {departureIcao} → {destinationIcao}
            </span>
            <span className="fp-globe-route-plate__aircraft fp-type-label-large">{aircraft}</span>
          </div>
          <div className="fp-globe-route-plate__chips">
            <ValueChip label="DIST" value={distance} containerAlpha={0.5} />
            {bearing != null && <ValueChip label="BRG" value={bearing} containerAlpha={0.5} />}
            {flightTime != null && <ValueChip label="TIME" value={flightTime} containerAlpha={0.5} />}
          </div>
        </div>
      </div>
    </div>
  )
}
