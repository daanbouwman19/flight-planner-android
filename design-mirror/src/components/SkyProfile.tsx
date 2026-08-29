import { useMemo } from 'react'
import {
  AXIS_BREAKPOINTS,
  CEILING_THRESHOLDS,
  altitudeToFraction,
  baseInsetFor,
  celestialAlpha,
  deckFractions,
  deckOpacity,
  deckShoulder,
  deckSpans,
  deckThicknesses,
  fogHeightFraction,
  lobeHeights,
  mergeDecks,
  railX,
  railY,
  type CloudLayer,
  type SkyCover,
  type SkyPhase,
} from '../geo/skyProfile'

/** The air is four flat steps whose edges are the flight-rules thresholds. */
const AIR_STEP_MIXES = [0.0, 0.2, 0.44, 0.78]
const HORIZON_WARMTH = 0.16
const CEILING_WEDGE = 6
const HAIRLINE = 1
const DECK_EDGE = 1.5

/** The two standard heights: airport detail and route detail. */
export const SkyProfileHeight = { AirportDetail: 220, RouteDetail: 168 } as const

export interface CelestialState {
  /** The sun's elevation in degrees above the horizon, at the observation instant. */
  sunElevationDeg: number
  /** The sun's azimuth in degrees true. */
  sunAzimuthDeg: number
  moonElevationDeg?: number
  moonAzimuthDeg?: number
  /** 0 new, 0.5 full, 1 new again. */
  moonPhase?: number
}

export interface SkyProfileProps {
  /** What the station reported about the sky. `unknown` hatches rather than guessing. */
  skyCover: SkyCover
  /** Present-weather fog or mist, which thickens the surface slab. */
  fogOrMist?: boolean
  visibilityStatuteMiles?: number | null
  /** The reported ceiling in feet, or `null` for unlimited, or omit for unknown. */
  ceilingFt?: number | null
  /**
   * Resolved by the caller **at the observation instant, never at now**.
   *
   * A current sun over a three-day-old report is two times in one picture, and a
   * component that read the clock could not be previewed at dusk.
   */
  celestial?: CelestialState | null
  /** Used for previews and for reports with no position. Defaults to `DAY`. */
  phase?: SkyPhase
  height?: number
  showAltitudeLabels?: boolean
  className?: string
}

const VB_WIDTH = 360

/**
 * A vertical cross-section of the sky over one station.
 *
 * **It is not a picture of the sky.** It is read the way an approach plate's
 * profile view is: altitude on the Y axis, every deck at its true base, the
 * flight-rules thresholds as hairlines, the ground at the bottom.
 *
 * The structure is what makes the original defect unrepresentable. A sun over an
 * IFR field was possible because the category used to be a label pinned beside a
 * cartoon; here the category is a *consequence of the geometry*, and there is no
 * arrangement of this drawing that shows a 700 ft overcast as a nice day.
 *
 * Three things can be unknown independently and each hatches: no report at all, an
 * unreported sky, an unreported surface. They compose.
 *
 * ```tsx
 * <SkyProfile
 *   skyCover={{ kind: 'layers', layers: [{ cover: 'OVERCAST', baseFt: 700 }] }}
 *   ceilingFt={700}
 * />
 * ```
 */
export function SkyProfile({
  skyCover,
  fogOrMist = false,
  visibilityStatuteMiles,
  ceilingFt,
  celestial,
  phase = 'DAY',
  height = SkyProfileHeight.AirportDetail,
  showAltitudeLabels = true,
  className,
}: SkyProfileProps) {
  const vbHeight = height

  const scene = useMemo(() => {
    const layers: CloudLayer[] = skyCover.kind === 'layers' ? skyCover.layers : []
    const decks = mergeDecks(layers)
    const fractions = deckFractions(decks)
    const thicknesses = deckThicknesses(decks, fractions)
    const fogFraction = fogHeightFraction(skyCover, fogOrMist, visibilityStatuteMiles)
    return { decks, fractions, thicknesses, fogFraction }
  }, [skyCover, fogOrMist, visibilityStatuteMiles])

  const { decks, fractions, thicknesses, fogFraction } = scene

  // The ground occupies the bottom of the frame; the air band is everything above.
  const groundHeight = Math.round(vbHeight * 0.09)
  const airBottom = vbHeight - groundHeight
  const airHeight = airBottom
  const yOf = (fraction: number) => airBottom - Math.min(Math.max(fraction, 0), 1) * airHeight

  const airKnown = skyCover.kind !== 'unknown'
  const bodyAlpha = airKnown ? celestialAlpha(skyCover) : 0

  const band = bandFor(phase)
  const nightWeight = phase === 'NIGHT' ? 1 : 0

  // The four air steps, with their edges at the flight-rules thresholds.
  const stepEdges = [0, ...CEILING_THRESHOLDS.map(([ft]) => altitudeToFraction(ft)), 1]

  return (
    <div
      className={['fp-sky-profile', className].filter(Boolean).join(' ')}
      style={{ height }}
    >
      <svg
        viewBox={`0 0 ${VB_WIDTH} ${vbHeight}`}
        preserveAspectRatio="none"
        role="img"
        aria-label={describe(skyCover, ceilingFt, fogFraction > 0)}
      >
        <defs>
          {/* One hatch, reused by every unknown. An absence a reader can see is a
              different thing from an absence they cannot. */}
          <pattern
            id="fp-sky-hatch"
            width="8"
            height="8"
            patternUnits="userSpaceOnUse"
            patternTransform="rotate(45)"
          >
            <line x1="0" y1="0" x2="0" y2="8" stroke="var(--fp-on-surface-variant)" strokeWidth="1" strokeOpacity="0.35" />
          </pattern>
        </defs>

        {/* The air, as four flat steps rather than a wash. It was a gradient, and
            that was the wrong mark twice over: a soft atmospheric wash beside the
            route card's flat map silhouette reads as a different application, and
            a wash puts its tone changes at heights that mean nothing. Each step is
            the air inside one band, so the tone change and the hairline are the
            same edge stated twice. */}
        {airKnown ? (
          stepEdges.slice(0, -1).map((bottom, i) => {
            const top = stepEdges[i + 1]
            const mix = AIR_STEP_MIXES[i]
            // The warmth is folded into each step's own colour rather than laid
            // over the stack: drawn as a gradient it would put back exactly the
            // wash the steps exist to remove. Even a cold sky is warmer near the
            // ground — the longer path through the atmosphere, the same reason a
            // low sun reddens — so it is strongest at the bottom and effectively
            // absent at the top. It follows the phase rather than switching off at
            // night, because the quantity it stands for is continuous.
            const reach = (1 - mix) * (1 - mix)
            const warmth = Math.round(HORIZON_WARMTH * (1 - nightWeight) * reach * 100)
            const air = `color-mix(in srgb, ${band.high} ${Math.round(mix * 100)}%, ${band.low})`
            return (
              <rect
                key={`air-${i}`}
                x={0}
                y={yOf(top)}
                width={VB_WIDTH}
                height={yOf(bottom) - yOf(top)}
                fill={
                  warmth > 0
                    ? `color-mix(in srgb, ${band.sunGlow} ${warmth}%, ${air})`
                    : air
                }
              />
            )
          })
        ) : (
          <rect x={0} y={0} width={VB_WIDTH} height={airBottom} fill="var(--fp-surface-container-highest)" />
        )}
        {!airKnown && (
          <rect x={0} y={0} width={VB_WIDTH} height={airBottom} fill="url(#fp-sky-hatch)" />
        )}

        {/* The celestial rail. The bodies ride fractions 0.86–0.935, well clear of
            the ruler: a body has an elevation *angle*, not an altitude in feet, so
            the layer must never invite a reader to drop a horizontal onto the
            axis. The highest threshold sits at 0.56, which is what guarantees it. */}
        {celestial != null && bodyAlpha > 0 && (
          <CelestialLayer
            celestial={celestial}
            alpha={bodyAlpha}
            yOf={yOf}
            band={band}
          />
        )}

        {/* The decks, each at its true base. */}
        {decks.map((deck, i) => (
          <Deck
            key={`deck-${i}`}
            deck={deck}
            fraction={fractions[i]}
            thickness={thicknesses[i]}
            yOf={yOf}
            band={band}
          />
        ))}

        {/* Fog: an opaque slab with a hard top edge, not a fade. Drawn as a
            gradient into the air it met the sky at about 1.1:1, so a half-mile
            field looked like a clear day with a wash at the bottom. Fog is not a
            tint on the air; it is a surface. */}
        {fogFraction > 0 && (
          <>
            <rect
              x={0}
              y={yOf(fogFraction)}
              width={VB_WIDTH}
              height={airBottom - yOf(fogFraction)}
              fill={band.cloudBody}
            />
            <line
              x1={0}
              y1={yOf(fogFraction)}
              x2={VB_WIDTH}
              y2={yOf(fogFraction)}
              stroke={band.cloudEdge}
              strokeWidth={DECK_EDGE}
            />
          </>
        )}

        {/* The graticule rides *over* the scene, and its numerals sit on chips.
            Under the decks it failed in exactly the case it is for: an overcast lid
            at 900 ft covers the 1,000 ft hairline, so the band structure disappears
            when the deck's position relative to it is the entire content. */}
        {CEILING_THRESHOLDS.map(([ft, rules]) => (
          <line
            key={`threshold-${ft}`}
            x1={0}
            y1={yOf(altitudeToFraction(ft))}
            x2={VB_WIDTH}
            y2={yOf(altitudeToFraction(ft))}
            stroke={`var(--fp-${rules.toLowerCase()}-container)`}
            strokeWidth={HAIRLINE}
          />
        ))}

        {showAltitudeLabels &&
          AXIS_BREAKPOINTS.slice(1, -1).map(([ft, fraction]) => (
            <g key={`label-${ft}`}>
              <rect
                x={6}
                y={yOf(fraction) - 7}
                width={label(ft).length * 5.6 + 8}
                height={14}
                rx={3}
                fill={band.chip}
              />
              <text
                x={10}
                y={yOf(fraction)}
                dominantBaseline="central"
                className="fp-sky-profile__label"
                fill={band.cloudEdge}
              >
                {label(ft)}
              </text>
            </g>
          ))}

        {/* The ceiling wedge, at the right edge. `Unlimited` gets the same wedge
            `At` does, at the top of the axis: an affirmatively clear sky used to
            draw nothing, so it differed from an unknown one only by the absence of
            hatch — absence of evidence, one layer up. */}
        {ceilingFt !== undefined && (
          <polygon
            points={wedgePoints(
              yOf(ceilingFt == null ? 1 : altitudeToFraction(ceilingFt)),
            )}
            fill={`var(--fp-${ceilingCategory(ceilingFt).toLowerCase()}-container)`}
          />
        )}

        {/* The ground. */}
        <rect x={0} y={airBottom} width={VB_WIDTH} height={groundHeight} fill="var(--fp-sky-ground-dry)" />
      </svg>
    </div>
  )
}

function Deck({
  deck,
  fraction,
  thickness,
  yOf,
  band,
}: {
  deck: ReturnType<typeof mergeDecks>[number]
  fraction: number
  thickness: number
  yOf: (f: number) => number
  band: Band
}) {
  const spans = deckSpans(deck)
  const opacity = deckOpacity(deck.cover)
  const inset = baseInsetFor(deck.cover)
  const shoulder = deckShoulder(deck.cover)
  const baseY = yOf(fraction)
  const topY = yOf(fraction + thickness)
  const deckHeight = baseY - topY

  return (
    <g opacity={opacity}>
      {spans.map((span, runIndex) => {
        const heights = lobeHeights(deck.cover, deck.baseFt, runIndex)
        const x0 = span.start * VB_WIDTH
        const x1 = span.end * VB_WIDTH
        const width = x1 - x0
        const lobeWidth = width / heights.length

        // The underside is flat — that edge is what a reader measures against the
        // hairlines — and the top is lobed.
        const parts: string[] = [`M${x0.toFixed(1)},${baseY.toFixed(1)}`]
        heights.forEach((h, i) => {
          const lx0 = x0 + i * lobeWidth
          const lx1 = lx0 + lobeWidth
          const peak = baseY - deckHeight * h
          const c = lobeWidth * (0.5 - shoulder * 0.25)
          parts.push(
            `C${(lx0 + c).toFixed(1)},${peak.toFixed(1)} ${(lx1 - c).toFixed(1)},${peak.toFixed(1)} ${lx1.toFixed(1)},${(i === heights.length - 1 ? baseY : baseY - deckHeight * (heights[i + 1] ?? h) * 0.6).toFixed(1)}`,
          )
        })
        parts.push(`L${x1.toFixed(1)},${baseY.toFixed(1)}Z`)

        return (
          <g key={runIndex}>
            <path d={parts.join('')} fill={band.cloudBody} />
            {/* The underside, in the band's own edge ink. A cloud's underside needs
                3:1 against the air, and no single ink clears that against both a
                bright day sky and a near-black night one — which is why the ink
                belongs to the band rather than to the component. */}
            <line
              x1={x0 + width * inset}
              y1={baseY}
              x2={x1 - width * inset}
              y2={baseY}
              stroke={band.cloudEdge}
              strokeWidth={DECK_EDGE}
            />
            {deck.convective != null && (
              <path
                d={`M${((x0 + x1) / 2).toFixed(1)},${baseY.toFixed(1)} l-4,10 l6,-2 l-4,12`}
                fill="none"
                stroke={band.convective}
                strokeWidth={2}
                strokeLinejoin="round"
              />
            )}
          </g>
        )
      })}
    </g>
  )
}

function CelestialLayer({
  celestial,
  alpha,
  yOf,
  band,
}: {
  celestial: CelestialState
  alpha: number
  yOf: (f: number) => number
  band: Band
}) {
  const sunX = railX(celestial.sunAzimuthDeg) * VB_WIDTH
  const sunY = yOf(railY(celestial.sunElevationDeg))
  const above = celestial.sunElevationDeg > 0
  return (
    <g opacity={alpha}>
      {above ? (
        <>
          <circle cx={sunX} cy={sunY} r={11} fill={band.sunGlow} opacity={0.5} />
          <circle cx={sunX} cy={sunY} r={7} fill={band.sun} />
        </>
      ) : celestial.moonElevationDeg != null && celestial.moonElevationDeg > 0 ? (
        <circle
          cx={railX(celestial.moonAzimuthDeg ?? 0) * VB_WIDTH}
          cy={yOf(railY(celestial.moonElevationDeg))}
          r={6}
          fill={band.moonLit}
        />
      ) : null}
    </g>
  )
}

interface Band {
  low: string
  high: string
  cloudBody: string
  cloudEdge: string
  convective: string
  sun: string
  sunGlow: string
  moonLit: string
  chip: string
}

/** The band's inks, as CSS variables so the scheme in scope resolves them. */
function bandFor(phase: SkyPhase): Band {
  const key = phase.toLowerCase()
  return {
    low: `var(--fp-sky-${key}-low)`,
    high: `var(--fp-sky-${key}-high)`,
    cloudBody: `var(--fp-sky-${key}-cloud-body)`,
    cloudEdge: `var(--fp-sky-${key}-cloud-edge)`,
    convective: `var(--fp-sky-${key}-convective)`,
    sun: 'var(--fp-sky-celestial-sun)',
    sunGlow: 'var(--fp-sky-celestial-sun-glow)',
    moonLit: 'var(--fp-sky-celestial-moon-lit)',
    // One known mix between the band's two ends: a numeral straight onto the scene
    // would have to clear its bound against whichever step, deck or fog slab it
    // landed on.
    chip: `color-mix(in srgb, var(--fp-sky-${key}-high) 38%, var(--fp-sky-${key}-low))`,
  }
}

const CEILING_WEDGE_DEPTH = CEILING_WEDGE * 2

function wedgePoints(y: number): string {
  const right = VB_WIDTH
  return `${right},${y - CEILING_WEDGE_DEPTH / 2} ${right},${y + CEILING_WEDGE_DEPTH / 2} ${right - CEILING_WEDGE},${y}`
}

function ceilingCategory(ceilingFt: number | null | undefined): string {
  if (ceilingFt == null) return 'VFR'
  if (ceilingFt < 500) return 'LIFR'
  if (ceilingFt < 1000) return 'IFR'
  if (ceilingFt <= 3000) return 'MVFR'
  return 'VFR'
}

function label(ft: number): string {
  return ft >= 1000 ? `${(ft / 1000).toFixed(0)},000` : String(ft)
}

function describe(
  skyCover: SkyCover,
  ceilingFt: number | null | undefined,
  fog: boolean,
): string {
  if (skyCover.kind === 'unknown') return 'Sky conditions not reported'
  if (skyCover.kind === 'obscured') return 'Sky obscured'
  const ceiling =
    ceilingFt == null ? 'no ceiling' : `ceiling ${ceilingFt.toLocaleString('en-US')} ft`
  const layers =
    skyCover.kind === 'layers' && skyCover.layers.length > 0
      ? skyCover.layers.map((l) => `${l.cover.toLowerCase()} at ${l.baseFt} ft`).join(', ')
      : 'clear'
  return `Sky profile: ${layers}, ${ceiling}${fog ? ', fog at the surface' : ''}`
}
