import { useLayoutEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { MapFrame, MIN_SPAN_DEGREES, sampleGeoArc } from '../geo/mapFrame'
import { worldOutline } from '../geo/worldOutline.gen'
import { useIsDark } from '../theme/FlightPlannerTheme'
import { polylineToPath, ringsToPath } from './RouteMap'

/**
 * The reference watch's face in dp: 480 px at 340 dpi, measured on the
 * SM-L350 (`docs/WEAR-PLAN.md`, *The target*). Everything on the face is placed
 * in fractions of this, so a concept drawn at another size keeps the text where
 * it sits *relative to the circle*, which is what the app's `RouteFace` holds.
 */
export const WATCH_FACE_DP = 226

// ── RouteFeedScreen.kt ────────────────────────────────────────────────────────
const TOP_GROUP_FRACTION = 0.17
const BOTTOM_GROUP_FRACTION = 0.12
const SIDE_INSET_TOP = 0.135
const SIDE_INSET_BOTTOM = 0.1
const AIRFRAME_SIDE_INSET = 0.2
const ARROW_GAP_DP = 5
const ARROW_WIDTH_DP = 18
const ARROW_HEIGHT_DP = 12
const ARROW_STROKE_DP = 2
const ARROW_BARB_FRACTION = 0.34
const CODE_MAX_SP = 30
const CODE_MIN_SP = 22
const FLASH_SIDE_INSET = 0.14

// ── WatchRouteMap.kt ──────────────────────────────────────────────────────────
const ROUTE_PADDING_FRACTION = 0.284
const TOP_INSET_FRACTION = 0.22
const OUTLINE_MARGIN = 0.08
const COAST_STROKE_DP = 1
const ROUTE_STROKE_DP = 2.5
const CASING_DP = 1.5
const ENDPOINT_RADIUS_DP = 3

/**
 * The ground every watch surface stands on: the theme's `background`, except
 * that the two dark looks go to **true black**. The display is OLED — an unlit
 * pixel costs nothing and lets the round bezel disappear under a full-bleed
 * map — so `WearFlightPlannerTheme` replaces the phone's `#111319` (brand dark)
 * and `#110A02` (Cockpit) with `#000000`. Chart stays paper: a paper chart that
 * went black would be a different theme. Every other role the watch paints is
 * the phone's own, which is why the rest of this file reads `--fp-*` directly.
 */
function useWatchGround(): string {
  return useIsDark() ? '#000000' : 'var(--fp-background)'
}

export interface WatchFrameProps {
  /** The face's diameter in dp. Defaults to {@link WATCH_FACE_DP}, the reference watch. */
  size?: number
  children?: ReactNode
  className?: string
}

/**
 * A round watch display, drawn the way the app occupies one: the whole circle,
 * edge to edge, on the watch's ground.
 *
 * There is no bezel, clock or page indicator: the app draws no `TimeText` and
 * no pager indicator, and a Wear activity has no system bars to leave room for.
 * Anything inside is clipped to the circle, which is the constraint every
 * layout decision on the watch is about — near the top and bottom the chord is
 * far narrower than the diameter, and text laid out against the *width* runs
 * under the glass. Lay a concept out against the chord at its own height.
 *
 * ```tsx
 * <WatchFrame>{…}</WatchFrame>
 * ```
 */
export function WatchFrame({ size = WATCH_FACE_DP, children, className }: WatchFrameProps) {
  const ground = useWatchGround()
  const style = { width: size, height: size, '--fp-watch-ground': ground } as CSSProperties
  return (
    <div className={['fp-watch-frame', className].filter(Boolean).join(' ')} style={style}>
      {children}
    </div>
  )
}

export interface WatchRouteMapProps {
  depLat: number
  depLon: number
  destLat: number
  destLon: number
  /** The face's diameter in dp; strokes are dp, so this sets their weight. Defaults to {@link WATCH_FACE_DP}. */
  size?: number
  className?: string
}

/**
 * The route over the world, filling the whole face — `WatchRouteMap.kt`.
 *
 * The same projection as {@link RouteMap} (`MapFrame`, `land.outline`, the
 * sampled great circle) drawn differently, because a round face wants a map
 * that bleeds past the bezel where a card wants one that fits inside it:
 *
 * - **Framed for a circle, not a square.** `paddingFraction` 0.284 rather than
 *   0.12 — the default leaves a diagonal route's ends outside the inscribed
 *   circle, off the glass — and the top 22 % is kept clear for the codes.
 * - **Land is solid `surfaceContainer`**, the plates' own ground, so a plate
 *   over land reads as a plate and not a hole. The coast is `outline` at 45 %.
 * - **The casing is the watch's ground**, so where the route crosses a coast it
 *   reads as a gap in the map rather than a dark outline.
 * - **Two filled dots, no arrow.** The codes above say which way the leg runs.
 *
 * No scrim over it, on any theme: the inks are quiet enough to carry the face's
 * text, and a scrim on a round face is a band across the middle of the circle.
 *
 * ```tsx
 * <WatchFrame><WatchRouteMap depLat={52.31} depLon={4.76} destLat={40.64} destLon={-73.78} /></WatchFrame>
 * ```
 */
export function WatchRouteMap({ depLat, depLon, destLat, destLon, size = WATCH_FACE_DP, className }: WatchRouteMapProps) {
  const vb = 1000
  const s = vb / size

  const scene = useMemo(() => {
    const arc = sampleGeoArc(depLat, depLon, destLat, destLon)
    const frame = MapFrame.forRoute(arc.lats, arc.lons, 1, MIN_SPAN_DEGREES, ROUTE_PADDING_FRACTION, TOP_INSET_FRACTION)
    const land = frame.projectOutline(worldOutline(), OUTLINE_MARGIN)
    const projected = frame.project(arc.lats, arc.lons)
    return {
      landPath: ringsToPath(land.fill, vb, vb, true),
      coastPath: ringsToPath(land.coast, vb, vb, false),
      routePath: polylineToPath(projected, vb, vb),
      ends: [
        [projected[0] * vb, projected[1] * vb],
        [projected[projected.length - 2] * vb, projected[projected.length - 1] * vb],
      ],
    }
  }, [depLat, depLon, destLat, destLon])

  const route = ROUTE_STROKE_DP * s
  const casing = CASING_DP * s
  const radius = ENDPOINT_RADIUS_DP * s

  return (
    <svg
      className={['fp-watch-map', className].filter(Boolean).join(' ')}
      viewBox={`0 0 ${vb} ${vb}`}
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      <path d={scene.landPath} fill="var(--fp-surface-container)" fillRule="evenodd" />
      <path
        d={scene.coastPath}
        fill="none"
        stroke="var(--fp-outline)"
        strokeOpacity={0.45}
        strokeWidth={COAST_STROKE_DP * s}
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      <path
        d={scene.routePath}
        fill="none"
        stroke="var(--fp-watch-ground)"
        strokeWidth={route + 2 * casing}
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      <path d={scene.routePath} fill="none" stroke="var(--fp-primary)" strokeWidth={route} strokeLinejoin="round" strokeLinecap="round" />
      {scene.ends.map(([x, y], i) => (
        <g key={i}>
          <circle cx={x} cy={y} r={radius + casing} fill="var(--fp-watch-ground)" />
          <circle cx={x} cy={y} r={radius} fill="var(--fp-primary)" />
        </g>
      ))}
    </svg>
  )
}

export interface WatchRoute {
  status: 'ready'
  departureIcao: string
  destinationIcao: string
  depLat: number
  depLon: number
  destLat: number
  destLon: number
  /** Already worded, always nautical miles — the watch has no unit setting. `5783 NM`. */
  distanceText: string
  /** `GreatCircle.flightTime(...).format()` — `12:00`. */
  eteText: string
  /** The airframe's full display name. It wraps rather than shrinking or ellipsising. */
  aircraftName: string
}

export type WatchFeedState = { status: 'loading' } | { status: 'unavailable' } | WatchRoute

export interface WatchRouteFaceProps {
  state: WatchFeedState
  /**
   * The flash reporting the last tap, centred over the face for two seconds.
   * `sent` once the phone has the route, `failed` when it could not be reached.
   */
  handoff?: 'sent' | 'failed' | null
  /** The face's diameter in dp. Defaults to {@link WATCH_FACE_DP}. */
  size?: number
  className?: string
}

/**
 * The watch app — `RouteFeedScreen.kt` — which is one screen: a route filling
 * the round face.
 *
 * The two codes across the top, the distance and time on translucent plates
 * and the airframe across the bottom, the great circle behind all of it. A
 * swipe or a turn of the bezel pages to the next route (the outgoing face
 * scales down and fades as the next rises, Wear's own `AnimatedPage`); a tap
 * anywhere opens the route on the phone, with a haptic tick and no ripple, and
 * the result comes back as a flash. The mirror draws one page, still.
 *
 * **The codes shrink; they never wrap.** 30 sp when the pair fits the chord at
 * that height, stepping down 1 sp at a time to a 22 sp floor when it does not —
 * `TextAutoSize.StepBased` in the app, a measured step-down here. A fixed
 * smaller size only moves the cliff: a letter-heavy code is always wider. The
 * airframe line is inset further than the plates above it, because the circle
 * is narrower there, and a long name wraps instead.
 *
 * The flash is centred, not at the top: the middle is the widest line on the
 * circle and the only place with nothing but map in it. It is not a dialog —
 * no scrim, no buttons, gone on its own.
 *
 * Not on the watch, on purpose (`docs/WEAR-PLAN.md`): flight-rules badges (no
 * `INTERNET` permission — absent rather than stale), kilometres, dynamic
 * colour, and marking a route as flown.
 *
 * ```tsx
 * <WatchRouteFace
 *   state={{
 *     status: 'ready',
 *     departureIcao: 'KCSM', destinationIcao: 'LTAT',
 *     depLat: 35.34, depLon: -99.2, destLat: 38.44, destLon: 38.09,
 *     distanceText: '5783 NM', eteText: '12:00', aircraftName: 'Boeing 777-200ER',
 *   }}
 * />
 * ```
 */
export function WatchRouteFace({ state, handoff = null, size = WATCH_FACE_DP, className }: WatchRouteFaceProps) {
  return (
    <WatchFrame size={size} className={['fp-watch-face', className].filter(Boolean).join(' ')}>
      {state.status === 'ready' ? (
        <RouteFace route={state} size={size} />
      ) : state.status === 'loading' ? (
        <CentredMessage title="Finding routes…" />
      ) : (
        <CentredMessage
          title="No routes right now"
          detail="The airport data could not be read. Open Flight Planner on your phone."
        />
      )}
      {handoff !== null && (
        <div className="fp-watch-flash">
          <span className="fp-watch-flash__pill" style={{ maxWidth: size * (1 - 2 * FLASH_SIDE_INSET) }}>
            {handoff === 'sent' ? 'Opened on your phone' : 'Could not reach your phone'}
          </span>
        </div>
      )}
    </WatchFrame>
  )
}

function RouteFace({ route, size }: { route: WatchRoute; size: number }) {
  const spell = (icao: string) => icao.split('').join(' ')
  return (
    <div
      className="fp-watch-route"
      role="img"
      aria-label={`Route ${spell(route.departureIcao)} to ${spell(route.destinationIcao)}, ${route.distanceText}, in a ${route.aircraftName}. Double tap to open it on your phone.`}
    >
      <WatchRouteMap depLat={route.depLat} depLon={route.depLon} destLat={route.destLat} destLon={route.destLon} size={size} />
      <div
        className="fp-watch-route__codes"
        style={{ top: size * TOP_GROUP_FRACTION, left: size * SIDE_INSET_TOP, right: size * SIDE_INSET_TOP }}
      >
        <IcaoCode code={route.departureIcao} />
        <RouteArrow />
        <IcaoCode code={route.destinationIcao} />
      </div>
      <div
        className="fp-watch-route__foot"
        style={{ bottom: size * BOTTOM_GROUP_FRACTION, left: size * SIDE_INSET_BOTTOM, right: size * SIDE_INSET_BOTTOM }}
      >
        <div className="fp-watch-route__plates">
          <ValuePlate label="DIST" value={route.distanceText} />
          <ValuePlate label="TIME" value={route.eteText} />
        </div>
        <span
          className="fp-watch-route__airframe"
          style={{ paddingInline: size * (AIRFRAME_SIDE_INSET - SIDE_INSET_BOTTOM) }}
        >
          {route.aircraftName}
        </span>
      </div>
    </div>
  )
}

/**
 * One code: 30 sp when it fits its half of the row, stepping down 1 sp at a
 * time to 22 sp when it does not, and never onto a second line. Each code is
 * capped at half of what the arrow leaves — `weight(1f, fill = false)` — so it
 * is not always the destination that runs out of room.
 */
function IcaoCode({ code }: { code: string }) {
  const ref = useRef<HTMLSpanElement>(null)
  const [fontSize, setFontSize] = useState(CODE_MAX_SP)

  useLayoutEffect(() => {
    const el = ref.current
    if (el === null) return
    let live = true
    const fit = () => {
      if (!live) return
      let sp = CODE_MAX_SP
      el.style.fontSize = `${sp}px`
      while (sp > CODE_MIN_SP && el.scrollWidth > el.clientWidth) {
        sp -= 1
        el.style.fontSize = `${sp}px`
      }
      setFontSize(sp)
    }
    fit()
    // Measured against the fallback face, a code can settle a step too large
    // and overflow once Roboto arrives, so fit again when it has.
    void document.fonts?.ready.then(fit)
    return () => {
      live = false
    }
  }, [code])

  return (
    <span
      ref={ref}
      className="fp-watch-route__code"
      style={{ fontSize, maxWidth: `calc(50% - ${ARROW_WIDTH_DP / 2 + ARROW_GAP_DP}px)` }}
    >
      {code}
    </span>
  )
}

/** The chevron between the codes — drawn, not a `→` glyph, which changes with the font and takes the text's weight. */
function RouteArrow() {
  const stroke = ARROW_STROKE_DP
  const midY = ARROW_HEIGHT_DP / 2
  const head = ARROW_WIDTH_DP - stroke / 2
  const barb = ARROW_HEIGHT_DP * ARROW_BARB_FRACTION
  return (
    <svg
      className="fp-watch-route__arrow"
      width={ARROW_WIDTH_DP}
      height={ARROW_HEIGHT_DP}
      style={{ marginInline: ARROW_GAP_DP }}
      aria-hidden="true"
    >
      <g fill="none" stroke="currentColor" strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round">
        <line x1={stroke / 2} y1={midY} x2={head} y2={midY} />
        <path d={`M${head - barb},${midY - barb}L${head},${midY}L${head - barb},${midY + barb}`} />
      </g>
    </svg>
  )
}

/** A figure and its caption on a plate at 72 % — translucent, so the coast behind reads as one continuous shape. */
function ValuePlate({ label, value }: { label: string; value: string }) {
  return (
    <div className="fp-watch-plate">
      <span className="fp-watch-plate__label">{label}</span>
      <span className="fp-watch-plate__value">{value}</span>
    </div>
  )
}

function CentredMessage({ title, detail }: { title: string; detail?: string }) {
  return (
    <div className="fp-watch-message">
      <span className="fp-watch-message__title">{title}</span>
      {detail !== undefined && <span className="fp-watch-message__detail">{detail}</span>}
    </div>
  )
}
