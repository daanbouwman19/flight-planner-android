import { useMemo } from 'react'
import {
  favouredEnd,
  laneRay,
  layoutRunways,
  pairPhysicalRunways,
  positionedRays,
  sockLift,
  type Point,
  type Runway,
  type RunwayRay,
} from '../geo/runwayLayout'

export type { Runway } from '../geo/runwayLayout'

/**
 * The reported surface wind.
 *
 * Its own type rather than two nullables, because *calm*, *variable* and *steady*
 * are three different facts — a `VRB` group is a real speed with no usable
 * direction, and a 0 kt report is the station saying the air is still — and no
 * combination of a nullable direction and a nullable speed expresses that without
 * the call site remembering which pairing means what.
 */
export interface DiagramWind {
  /** Degrees the wind blows **from**. Null when the report gives no direction. */
  directionFromDeg?: number | null
  speedKt: number
  gustKt?: number | null
  /** A `VRB` group: a real speed, no usable direction. */
  variable?: boolean
}

export interface RunwayDiagramProps {
  /** Every end of the field. Ends with no true heading are counted, not drawn. */
  runways: Runway[]
  /**
   * When present the diagram grows a windsock and highlights the end a pilot
   * would most likely use.
   *
   * **This is why the wind belongs here rather than only in a weather panel.** A
   * direction in degrees has to be compared against a runway heading in the
   * reader's head, and that comparison is the whole question. Drawn in the same
   * frame as the runways it stops being a comparison and becomes a picture: the
   * sock points down the strip or across it.
   */
  wind?: DiagramWind | null
  /** Pixel size of the square diagram. Defaults to `220`. */
  size?: number
  className?: string
}

const VIEWBOX = 220
const LANE_SPACING_FRACTION = 0.14
const WIDE_RUNWAY_FT = 100
const NARROW_STROKE = 2
const WIDE_STROKE = 4
const LABEL_MARGIN = 18
const LABEL_PUSH = 11
const FAVOURED_STROKE = 7
const FAVOURED_ALPHA = 0.3
const SOCK_OFFSET_FRACTION = 0.62
const SOCK_MAST_RADIUS_FRACTION = 0.016
const SOCK_LENGTH_FRACTION = 0.3
const SOCK_THROAT_FRACTION = 0.03

/**
 * The field's runways, as a plan view.
 *
 * **It draws a true plan when the data has one and a compass schematic when it
 * does not** — see `runwayLayout.ts`. Both are honest; which one appears is a
 * property of what OurAirports publishes for that field, not a style.
 *
 * A designator sits at its **threshold** — the end the runway is entered from,
 * which is the opposite end from where it points. Every FAA and Jeppesen plate
 * uses that convention, and putting the numeral at the wrong end tells a reader to
 * roll the wrong way down the strip.
 *
 * With a wind, the halo says *which strip* and the bold sock-orange numeral says
 * *which end of it* — and the second is the question actually being asked.
 *
 * ```tsx
 * <RunwayDiagram
 *   runways={[
 *     { ident: '09', lengthFt: 12467, trueHeadingDeg: 87, hardSurface: true },
 *     { ident: '27', lengthFt: 12467, trueHeadingDeg: 267, hardSurface: true },
 *   ]}
 *   wind={{ directionFromDeg: 250, speedKt: 14 }}
 * />
 * ```
 */
export function RunwayDiagram({
  runways,
  wind,
  size = 220,
  className,
}: RunwayDiagramProps) {
  const scene = useMemo(() => {
    const diagrammed = runways.filter((r) => r.trueHeadingDeg != null)
    const excludedCount = runways.length - diagrammed.length
    if (diagrammed.length === 0) {
      return { rays: [] as RunwayRay[], diagrammed, excludedCount, favoured: null as number | null }
    }

    const maxLengthFt = Math.max(...diagrammed.map((r) => r.lengthFt))
    const center: Point = { x: VIEWBOX / 2, y: VIEWBOX / 2 }
    const radius = VIEWBOX / 2 - LABEL_MARGIN

    // Both layouts need the pairing: the positioned one draws each strip
    // threshold-to-threshold, and the schematic needs to know whether an end *has*
    // an opposite number, because that decides where its designator goes.
    const groups = pairPhysicalRunways(diagrammed)
    const positioned = diagrammed.every((r) => r.latitude != null && r.longitude != null)

    let rays: RunwayRay[]
    if (positioned) {
      rays = positionedRays(diagrammed, groups, center, radius, maxLengthFt)
    } else {
      const offsets = layoutRunways(diagrammed)
      const paired = new Set<number>()
      for (const g of groups) if (g.runwayIndices.length > 1) for (const i of g.runwayIndices) paired.add(i)
      rays = diagrammed.map((r, i) =>
        laneRay(
          r,
          offsets[i] * radius * LANE_SPACING_FRACTION,
          center,
          radius,
          maxLengthFt,
          paired.has(i),
        ),
      )
    }

    const favoured = favouredEnd(
      // To the nearest degree: half a degree of runway heading is 0.17 kt of
      // crosswind in a 20 kt wind, well under the resolution of the report.
      diagrammed.map((r) => Math.round(r.trueHeadingDeg!)),
      wind && wind.directionFromDeg != null && !wind.variable ? wind.directionFromDeg : null,
      wind?.speedKt,
    )

    return { rays, diagrammed, excludedCount, favoured }
  }, [runways, wind])

  const { rays, diagrammed, excludedCount, favoured } = scene

  return (
    <div className={['fp-runway-diagram', className].filter(Boolean).join(' ')} style={{ width: size }}>
      <svg
        viewBox={`0 0 ${VIEWBOX} ${VIEWBOX}`}
        width={size}
        height={size}
        role="img"
        aria-label={runwayDescription(diagrammed, wind, favoured)}
      >
        {/* The halo first, under everything: it says which strip, and it must not
            sit on top of the line it is pointing at. */}
        {favoured != null && rays[favoured] != null && (
          <line
            x1={rays[favoured].origin.x}
            y1={rays[favoured].origin.y}
            x2={rays[favoured].tip.x}
            y2={rays[favoured].tip.y}
            stroke="var(--fp-sky-sock-band)"
            strokeOpacity={FAVOURED_ALPHA}
            strokeWidth={FAVOURED_STROKE}
            strokeLinecap="round"
          />
        )}

        {rays.map((ray, i) => {
          const runway = diagrammed[i]
          const wide = (runway.widthFt ?? 0) >= WIDE_RUNWAY_FT
          return (
            <line
              key={`${runway.ident}-${i}`}
              x1={ray.origin.x}
              y1={ray.origin.y}
              x2={ray.tip.x}
              y2={ray.tip.y}
              stroke={runway.hardSurface === false ? 'var(--fp-on-surface-variant)' : 'var(--fp-on-surface)'}
              strokeWidth={wide ? WIDE_STROKE : NARROW_STROKE}
              strokeLinecap="butt"
            />
          )
        })}

        {rays.map((ray, i) => {
          const runway = diagrammed[i]
          const isFavoured = favoured === i
          const label = pushOutward(ray.threshold, { x: VIEWBOX / 2, y: VIEWBOX / 2 }, LABEL_PUSH)
          return (
            <text
              key={`ident-${runway.ident}-${i}`}
              x={label.x}
              y={label.y}
              textAnchor="middle"
              dominantBaseline="central"
              className="fp-runway-diagram__ident"
              fill={isFavoured ? 'var(--fp-sky-sock-band)' : 'var(--fp-on-surface)'}
              fontWeight={isFavoured ? 700 : 500}
            >
              {runway.ident}
            </text>
          )
        })}

        {wind != null && <Windsock wind={wind} />}
      </svg>

      {excludedCount > 0 && (
        <div className="fp-runway-diagram__note fp-type-label-small">
          {excludedCount === 1
            ? '1 end has no published heading'
            : `${excludedCount} ends have no published heading`}
        </div>
      )}
    </div>
  )
}

/**
 * The windsock, drawn as a plan view.
 *
 * **The mast is a dot and the sock does not droop**: from directly above you
 * cannot see a sock hang, you see it foreshortened. A version that tilted it made
 * the sock rise *above* its own mast in a northerly, because the droop was being
 * added to the bearing's screen-space Y component — a quantity a top-down view
 * does not have. Speed shows as length instead.
 */
function Windsock({ wind }: { wind: DiagramWind }) {
  const mastX = VIEWBOX / 2 + (VIEWBOX / 2) * SOCK_OFFSET_FRACTION
  const mastY = VIEWBOX / 2 + (VIEWBOX / 2) * SOCK_OFFSET_FRACTION
  const lift = sockLift(wind.speedKt)
  const mastRadius = VIEWBOX * SOCK_MAST_RADIUS_FRACTION
  const length = VIEWBOX * SOCK_LENGTH_FRACTION * Math.max(lift, 0.18)
  const throat = VIEWBOX * SOCK_THROAT_FRACTION

  // A sock streams *away* from where the wind comes from.
  const hasDirection = wind.directionFromDeg != null && !wind.variable
  const towardDeg = hasDirection ? wind.directionFromDeg! + 180 : 0
  const rad = (towardDeg * Math.PI) / 180
  const dx = Math.sin(rad)
  const dy = -Math.cos(rad)
  const px = -dy
  const py = dx

  // A sock tapers toward its mouth; the stripes below interpolate between the two
  // half-widths rather than drawing a parallel-sided tube.
  const tipHalf = throat * 0.45

  const bands = 5
  const stripes = []
  for (let i = 0; i < bands; i++) {
    const t0 = i / bands
    const t1 = (i + 1) / bands
    const w0 = throat * (1 - t0) + tipHalf * t0
    const w1 = throat * (1 - t1) + tipHalf * t1
    const x0 = mastX + dx * length * t0
    const y0 = mastY + dy * length * t0
    const x1 = mastX + dx * length * t1
    const y1 = mastY + dy * length * t1
    stripes.push(
      <path
        key={i}
        d={
          `M${x0 + px * w0},${y0 + py * w0}` +
          `L${x1 + px * w1},${y1 + py * w1}` +
          `L${x1 - px * w1},${y1 - py * w1}` +
          `L${x0 - px * w0},${y0 - py * w0}Z`
        }
        fill={i % 2 === 0 ? 'var(--fp-sky-sock-band)' : 'var(--fp-sky-sock-alternate-band)'}
        // The pale bands are near-white by design — a real sock alternates
        // orange and white — so on a light surface they would read as gaps
        // between four floating dashes rather than as one object. The outline is
        // what holds the sock together at this size.
        stroke="var(--fp-sky-sock-band)"
        strokeWidth={0.5}
        strokeLinejoin="round"
      />,
    )
  }

  return (
    <g
      className={wind.speedKt > 3 ? 'fp-windsock fp-windsock--flying' : 'fp-windsock'}
      style={{ transformOrigin: `${mastX}px ${mastY}px` }}
    >
      {stripes}
      <circle cx={mastX} cy={mastY} r={mastRadius} fill="var(--fp-sky-sock-mast)" />
      {!hasDirection && (
        <text
          x={mastX}
          y={mastY + throat * 3}
          textAnchor="middle"
          className="fp-runway-diagram__ident"
          fill="var(--fp-on-surface-variant)"
        >
          VRB
        </text>
      )}
    </g>
  )
}

/** Pushes a point further from the centre, so an ident clears its own line. */
function pushOutward(point: Point, center: Point, by: number): Point {
  const dx = point.x - center.x
  const dy = point.y - center.y
  const magnitude = Math.hypot(dx, dy)
  if (magnitude < 1e-3) return point
  return { x: point.x + (dx / magnitude) * by, y: point.y + (dy / magnitude) * by }
}

function runwayDescription(
  runways: Runway[],
  wind: DiagramWind | null | undefined,
  favoured: number | null,
): string {
  if (runways.length === 0) return 'No diagrammable runways'
  const idents = runways.map((r) => r.ident).join(', ')
  const base = `Runways ${idents}`
  if (wind == null) return base
  const windText =
    wind.directionFromDeg != null && !wind.variable
      ? `wind ${wind.directionFromDeg}° at ${wind.speedKt} knots`
      : `wind variable at ${wind.speedKt} knots`
  const pick = favoured != null ? `, favouring runway ${runways[favoured].ident}` : ''
  return `${base}, ${windText}${pick}`
}
