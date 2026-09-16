import { useMemo, type ReactNode } from 'react'
import { GlobeView, GLOBE_VB_HEIGHT } from './GlobeView'
import { GlobeCameraControls, GlobeAttribution } from './GlobeChrome'
import type { FlightRules } from './FlightRulesBadge'
import { sampleGeoArc } from '../geo/mapFrame'
import { frameRoute, projectArc, projectPoint, type GlobeViewport } from '../geo/globeFrame'

export interface GlobeHeroEnd {
  icao: string
  /** Degrees north. */
  lat: number
  /** Degrees east. */
  lon: number
  rules?: FlightRules
}

const ARC_STROKE = 6
const ARC_CASING = ARC_STROKE + 8
const DOT_RADIUS = 9
/** Below this screen separation the two plates would overlap, so they merge. `MergeDistancePx`. */
const MERGE_FRACTION = 0.13
/** Roughly 12° of the horizon value, over which a label fades toward the limb. `LabelFadeSpan`. */
const LABEL_FADE_SPAN = 0.12

const clamp01 = (x: number): number => Math.min(1, Math.max(0, x))

export interface GlobeRouteSceneProps {
  departure: GlobeHeroEnd
  destination: GlobeHeroEnd
  /** The box's width / height. The projection is solved for this. */
  aspect: number
  /**
   * Fraction of the box the host's own chrome covers at the top. A label whose
   * dot rises into it fades rather than moving off its airport — case 2 of
   * `GlobeLabels`.
   */
  topChromeFraction?: number
  /** Chrome placed over the glass by the caller — the camera stack, the credit, a plate. */
  chrome?: ReactNode
  className?: string
}

/**
 * The route on the sphere: the arc bending over the curve, the two endpoint
 * dots, and the DEP/DEST plates anchored to their projected points.
 *
 * Ported from `GlobeSurface` + `GlobeLabels`. Shared by {@link GlobeHero} and
 * {@link ImmersiveGlobeScreen}, which differ only in where their chrome sits.
 * The plates follow the one rule that governs all of `GlobeLabels`: **a label is
 * a consequence of its point, so it never moves away from it — it drops detail
 * instead.** Near the limb it fades; too close to its partner it merges into one
 * plate holding both codes; under the chrome it fades.
 */
export function GlobeRouteScene({
  departure,
  destination,
  aspect,
  topChromeFraction = 0,
  chrome,
  className,
}: GlobeRouteSceneProps) {
  const scene = useMemo(() => {
    const vbHeight = GLOBE_VB_HEIGHT
    const vbWidth = Math.round(vbHeight * aspect)
    const viewport: GlobeViewport = { width: vbWidth, height: vbHeight }

    const camera = frameRoute(departure.lat, departure.lon, destination.lat, destination.lon, viewport)
    const arc = sampleGeoArc(departure.lat, departure.lon, destination.lat, destination.lon, 128)
    const arcPath = projectArc(arc.lats, arc.lons, camera, viewport)

    const dep = projectPoint(departure.lat, departure.lon, camera, viewport)
    const dest = projectPoint(destination.lat, destination.lon, camera, viewport)

    const chromePx = topChromeFraction * vbHeight
    const fadePx = 0.09 * vbHeight
    // A plate hangs below its dot, so a dot in the last plate-height of the box
    // has nowhere to put one; the globe clips to its bounds, so it fades instead
    // of being sliced. `PlateReserve` in GlobeLabels.
    const plateReservePx = 0.09 * vbHeight
    const horizon = camera.horizon

    const alphaFor = (
      p: { x: number; y: number; facing: number; visible: boolean } | null,
    ): number => {
      if (!p || !p.visible) return 0
      const limbFade = clamp01((p.facing - horizon) / (Math.abs(horizon) * LABEL_FADE_SPAN + 1e-4))
      const chromeFade = chromePx <= 0 ? 1 : clamp01((p.y - chromePx) / fadePx)
      const bottomFade = clamp01((vbHeight - p.y) / plateReservePx)
      return limbFade * chromeFade * bottomFade
    }

    const separation =
      dep && dest ? Math.hypot(dest.x - dep.x, dest.y - dep.y) : Number.POSITIVE_INFINITY

    return {
      camera,
      vbWidth,
      vbHeight,
      arcPath,
      dep,
      dest,
      depAlpha: alphaFor(dep),
      destAlpha: alphaFor(dest),
      merged: separation < MERGE_FRACTION * vbHeight,
      mid:
        dep && dest ? { x: (dep.x + dest.x) / 2, y: (dep.y + dest.y) / 2 } : null,
    }
  }, [departure.lat, departure.lon, destination.lat, destination.lon, aspect, topChromeFraction])

  const pct = (v: number, total: number) => `${((v / total) * 100).toFixed(2)}%`

  return (
    <GlobeView
      camera={scene.camera}
      aspect={aspect}
      className={className}
      scene={
        <>
          {scene.arcPath !== '' && (
            <>
              <path
                d={scene.arcPath}
                fill="none"
                stroke="var(--fp-surface-container)"
                strokeWidth={ARC_CASING}
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              <path
                d={scene.arcPath}
                fill="none"
                stroke="var(--fp-primary)"
                strokeWidth={ARC_STROKE}
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </>
          )}
          {scene.dep?.visible && (
            <circle cx={scene.dep.x} cy={scene.dep.y} r={DOT_RADIUS} fill="var(--fp-primary)" />
          )}
          {scene.dest?.visible && (
            <circle cx={scene.dest.x} cy={scene.dest.y} r={DOT_RADIUS} fill="var(--fp-tertiary)" />
          )}
        </>
      }
      overlay={
        <>
          {scene.merged && scene.mid ? (
            <LabelPlate
              left={pct(scene.mid.x, scene.vbWidth)}
              top={pct(scene.mid.y, scene.vbHeight)}
              alpha={Math.min(scene.depAlpha, scene.destAlpha)}
            >
              <span className="fp-globe-label__dots">
                <span className="fp-globe-label__dot" style={{ background: 'var(--fp-primary)' }} />
                <span className="fp-globe-label__dot" style={{ background: 'var(--fp-tertiary)' }} />
              </span>
              <span className="fp-globe-label__code">
                {departure.icao} · {destination.icao}
              </span>
            </LabelPlate>
          ) : (
            <>
              {scene.dep && (
                <LabelPlate
                  left={pct(scene.dep.x, scene.vbWidth)}
                  top={pct(scene.dep.y, scene.vbHeight)}
                  alpha={scene.depAlpha}
                >
                  <span className="fp-globe-label__dot" style={{ background: 'var(--fp-primary)' }} />
                  <span className="fp-globe-label__code">{departure.icao}</span>
                </LabelPlate>
              )}
              {scene.dest && (
                <LabelPlate
                  left={pct(scene.dest.x, scene.vbWidth)}
                  top={pct(scene.dest.y, scene.vbHeight)}
                  alpha={scene.destAlpha}
                >
                  <span
                    className="fp-globe-label__dot"
                    style={{ background: 'var(--fp-tertiary)' }}
                  />
                  <span className="fp-globe-label__code">{destination.icao}</span>
                </LabelPlate>
              )}
            </>
          )}
          {chrome}
        </>
      }
    />
  )
}

export interface GlobeHeroProps {
  departure: GlobeHeroEnd
  destination: GlobeHeroEnd
  /**
   * The hero box's width / height. Default is the deep hero's own shape — 44 %
   * of a 360 × 800 window, near square. `DeepGlobeHero.HeroFraction` in the app.
   */
  aspect?: number
  /** Fraction of the hero the app bar covers at the top; labels rising into it fade. */
  topChromeFraction?: number
  /** Draw the camera control stack. Default true — hidden where there is no renderer. */
  controls?: boolean
  className?: string
}

/**
 * The deep hero: the globe claiming the top of the route detail screen.
 *
 * Ported from `DeepGlobeHero`. It runs full bleed under the status bar — the
 * app's empty-bars invariant applied to the one surface that is a photograph —
 * and the chrome over it sits on the glass plates `GlobeCameraControls` draws.
 *
 * ### What is faithful and what is a stand-in
 *
 * The framing is the app's `GlobeFit`, aspect-aware; the arc bends over the
 * curve; the DEP/DEST plates behave exactly as `GlobeLabels` describes. The
 * sphere is an outline globe rather than NASA imagery — the runtime has no GPU —
 * which is recorded in `geo/globeFrame.ts`.
 *
 * ### What is deliberately not on the glass
 *
 * The concept moved DIST and BRG onto the sphere as chips. `DeepGlobeHero`'s
 * KDoc records why they are not: the real route detail's spine already states
 * every figure where it is true. The immersive screen, which has no spine, *does*
 * carry the plate of figures — see {@link ImmersiveGlobeScreen}.
 */
export function GlobeHero({
  departure,
  destination,
  aspect = 360 / 352,
  topChromeFraction = 0,
  controls = true,
  className,
}: GlobeHeroProps) {
  return (
    <GlobeRouteScene
      departure={departure}
      destination={destination}
      aspect={aspect}
      topChromeFraction={topChromeFraction}
      className={['fp-globe-hero', className].filter(Boolean).join(' ')}
      chrome={
        <>
          {controls && (
            <div className="fp-globe-hero__controls">
              <GlobeCameraControls />
            </div>
          )}
          <div className="fp-globe-hero__credit">
            <GlobeAttribution />
          </div>
        </>
      }
    />
  )
}

/** A dot and the code under it, anchored by the dot and hanging below it. `Plate` in GlobeLabels. */
function LabelPlate({
  left,
  top,
  alpha,
  children,
}: {
  left: string
  top: string
  alpha: number
  children: ReactNode
}) {
  if (alpha <= 0.01) return null
  return (
    <div className="fp-globe-label" style={{ left, top, opacity: alpha }}>
      {children}
    </div>
  )
}
