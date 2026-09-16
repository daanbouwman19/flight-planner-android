import { useMemo, useState } from 'react'
import { MapFrame, arcShifts, nodeSizeFraction, sampleGeoArc, unwrapLongitudeSet, type ProjectedRings } from '../geo/mapFrame'
import { worldOutline } from '../geo/worldOutline.gen'
import {
  ARROW_LENGTH,
  CASING,
  COAST_STROKE,
  ENDPOINT_RADIUS,
  MIN_ARROW_CHORD,
  OUTLINE_MARGIN,
  ROUTE_STROKE,
  WORLD_MAP_COAST_ALPHA,
  WORLD_MAP_LAND_ALPHA,
  arrowPath,
  projectedChord,
  scaleFor,
} from './RouteMap'
import { ModeSelector } from './ModeSelector'
import { GlobeNetwork } from './GlobeNetwork'

export interface HeroDistanceCardProps {
  /** Already formatted — `48,213 NM`. */
  totalDistance: string
  /** The figure made graspable — `2.2 × around the Earth`. */
  earthCircumferences: string
  className?: string
}

/**
 * The statistics headline: total distance, and what that distance *means*.
 *
 * The pill under the figure is the point. `48,213 NM` is a number a reader cannot
 * hold; "2.2 × around the Earth" is one they can, and it costs a line. In the app
 * the figure counts up once when it appears or changes — the motion principle's
 * own example of an animation worth having.
 */
export function HeroDistanceCard({
  totalDistance,
  earthCircumferences,
  className,
}: HeroDistanceCardProps) {
  return (
    <div className={['fp-stats-hero', className].filter(Boolean).join(' ')}>
      <span className="fp-stats-hero__label fp-type-label-small">TOTAL DISTANCE</span>
      <span className="fp-stats-hero__value fp-type-headline-large">{totalDistance}</span>
      <span className="fp-stats-hero__pill fp-type-label-small">{earthCircumferences}</span>
    </div>
  )
}

export interface MetricGridProps {
  /** Four is the usual count — two rows of two on a phone. */
  metrics: Array<{ label: string; value: string }>
  className?: string
}

/** The secondary figures under the hero, each a caption and a value. */
export function MetricGrid({ metrics, className }: MetricGridProps) {
  return (
    <div className={['fp-stats-metrics', className].filter(Boolean).join(' ')}>
      {metrics.map((m) => (
        <div className="fp-stats-metric" key={m.label}>
          <span className="fp-stats-metric__value fp-type-title-large">{m.value}</span>
          <span className="fp-stats-metric__label fp-type-label-small">{m.label}</span>
        </div>
      ))}
    </div>
  )
}

export interface MonthlyActivityCardProps {
  /** Flights per month, oldest first. */
  months: Array<{ label: string; value: number }>
  title?: string
  className?: string
}

/**
 * Flights per month, as a bar column.
 *
 * Bars rather than a line: the quantity is a count of discrete events per bucket,
 * and a line between two months claims a continuity that flying does not have.
 */
export function MonthlyActivityCard({
  months,
  title = 'Flights by month',
  className,
}: MonthlyActivityCardProps) {
  const peak = Math.max(1, ...months.map((m) => m.value))
  return (
    <div className={['fp-screen__card', className].filter(Boolean).join(' ')}>
      <span className="fp-screen__card-title fp-type-label-large">{title}</span>
      <div className="fp-stats-bars">
        {months.map((m, i) => (
          <div className="fp-stats-bars__column" key={`${m.label}-${i}`}>
            <div
              className="fp-stats-bars__bar"
              style={{ height: `${Math.round((m.value / peak) * 100)}%` }}
              title={`${m.label}: ${m.value}`}
            />
            <span className="fp-stats-bars__label fp-type-label-small">{m.label}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

export interface RankedRow {
  /** The thing being ranked. */
  name: string
  /** A short leading identifier, drawn in figures — an ICAO code. */
  code?: string
  count: number
}

export interface RankedListCardProps {
  title: string
  rows: RankedRow[]
  className?: string
}

/**
 * A ranked list — most-flown airframes, most-visited airports.
 *
 * One component for both, because they are the same object: a name, an optional
 * code, and a count aligned down the trailing edge in tabular figures.
 */
export function RankedListCard({ title, rows, className }: RankedListCardProps) {
  return (
    <div className={['fp-screen__card', className].filter(Boolean).join(' ')}>
      <span className="fp-screen__card-title fp-type-label-large">{title}</span>
      {rows.map((row) => (
        <div className="fp-stats-line" key={row.code ?? row.name}>
          <span className="fp-stats-line__name fp-type-body-medium">
            {row.code != null && <strong>{row.code}</strong>} {row.name}
          </span>
          <span className="fp-screen__row-figure fp-type-label-large">{row.count}</span>
        </div>
      ))}
    </div>
  )
}

export interface VisitedAirport {
  icao: string
  lat: number
  lon: number
  /** How many times it has been visited; drives the marker's radius. */
  visits?: number
}

export interface VisitedLeg {
  from: [number, number]
  to: [number, number]
}

export interface VisitedNetworkCardProps {
  airports: VisitedAirport[]
  /** The legs actually flown, as coordinate pairs. */
  legs?: VisitedLeg[]
  title?: string
  /**
   * Whether the device can draw a globe. When true the card grows a Flat/Globe
   * toggle in its header, **Flat first** — a fitted rectangle is right for a
   * regional logbook, the sphere for one that has outgrown a rectangle. The
   * toggle is **absent**, not disabled, where there is no renderer (3B). Default
   * false, so the card is unchanged unless a concept opts in.
   */
  globeAvailable?: boolean
  /**
   * Which projection the toggle starts on. `flat` in the app (`rememberSaveable`);
   * a concept can seed `globe` to show that state. Only meaningful with
   * `globeAvailable`.
   */
  initialView?: 'flat' | 'globe'
  className?: string
}

const VB_HEIGHT = 500

/** A field visited once: the route card's own endpoint, so the two maps agree. */
const NODE_MIN_RADIUS = ENDPOINT_RADIUS
/** The most-visited field in the set. */
const NODE_MAX_RADIUS = 9

/**
 * Every airport visited, and the legs between them, on one world map.
 *
 * Ported field-for-field from `NetworkMap` in `:core:designsystem` — **it is
 * `RouteMap`'s ink, not a second map.** A first version of this card wrote its
 * own numbers (a thinner leg, no casing, no arrowheads, a `tertiary`-coloured
 * dot sized from zero) and, beside a real route card, read as a sketch of one.
 * Every stroke here is drawn from the constants `RouteMap` exports, so the two
 * are one map at two scales.
 *
 * The window is fitted to the **visited set** rather than to the whole globe — but
 * only down to `MIN_SPAN_DEGREES`, the same 25° floor `MapFrame` applies to a route.
 * A logbook confined to the Low Countries therefore still shows most of north-west
 * Europe with the fields clustered in the middle of it, and that is deliberate: below
 * about that span the coastline stops being recognisable and the map becomes a
 * featureless rectangle with dots on it. Do not lower the floor for this card alone —
 * the constant belongs to the Kotlin, and a network drawn at a different zoom from a
 * route would stop being the same world.
 *
 * A dot's radius runs from `NODE_MIN_RADIUS` to `NODE_MAX_RADIUS` by
 * `nodeSizeFraction`: the least-visited field is the small dot, the
 * most-visited the large one, scaled from the set's own minimum rather than
 * from zero, so a logbook where every field has one visit — every new logbook —
 * draws every dot small instead of every dot at the maximum.
 */
export function VisitedNetworkCard({
  airports,
  legs = [],
  title = 'Where you have been',
  globeAvailable = false,
  initialView = 'flat',
  className,
}: VisitedNetworkCardProps) {
  const aspect = 16 / 10
  const vbWidth = Math.round(VB_HEIGHT * aspect)
  const s = scaleFor(VB_HEIGHT)
  const [view, setView] = useState<0 | 1>(initialView === 'globe' ? 1 : 0)
  const showGlobe = globeAvailable && view === 1 && airports.length > 0

  const scene = useMemo(() => {
    if (airports.length === 0) return null

    // The frame is fitted to every visited point at once, which is what makes
    // the card about this logbook rather than about the planet.
    //
    // **Unwrapped as a set first**, by the widest-gap seam — `MapFrame.forRoute`
    // documents its input as unwrapped longitudes, and raw ones break the
    // antimeridian: RJTT at 139.8°E and KLAX at 118.4°W fit a window centred
    // near 10°E spanning 258°, which covers Eurasia and the Atlantic.
    const lats = airports.map((a) => a.lat)
    const lons = unwrapLongitudeSet(airports.map((a) => a.lon))
    const frame = MapFrame.forRoute(lats, lons, aspect)
    const land = frame.projectOutline(worldOutline(), OUTLINE_MARGIN)
    const isEmpty = (r: ProjectedRings) => r.ringStart.length <= 1
    const graticulePath = isEmpty(land.coast)
      ? ringsToPath(frame.graticule(), vbWidth, VB_HEIGHT, false)
      : ''

    const arrowLength = ARROW_LENGTH * s
    const minArrowChord = MIN_ARROW_CHORD * s
    const legPaths: string[] = []
    const arrowPaths: string[] = []
    for (const leg of legs) {
      // Sampled in the direction it was first flown, then unwrapped along
      // its own great circle by `sampleGeoArc` — a walk relative to its own
      // departure, which may sit a turn away from where the frame put that
      // airport. `arcShifts` brings each end into the frame's own turn; a leg
      // whose ends fall in different turns is drawn at both.
      const arc = sampleGeoArc(leg.from[0], leg.from[1], leg.to[0], leg.to[1], 64)
      for (const shift of arcShifts(arc.lons[0], arc.lons[arc.lons.length - 1], frame.centreLon)) {
        const legLons = shift === 0 ? arc.lons : arc.lons.map((l) => l + shift)
        const projected = frame.project(arc.lats, legLons)
        legPaths.push(polylineToPath(projected, vbWidth, VB_HEIGHT))
        if (projectedChord(projected, vbWidth, VB_HEIGHT) >= minArrowChord) {
          const head = arrowPath(projected, Math.floor(projected.length / 4), vbWidth, VB_HEIGHT, arrowLength)
          if (head !== '') arrowPaths.push(head)
        }
      }
    }

    const visits = airports.map((a) => a.visits ?? 1)
    const minVisits = Math.min(...visits)
    const maxVisits = Math.max(...visits)
    const markers = airports.map((a, i) => ({
      icao: a.icao,
      x: frame.x(lons[i]) * vbWidth,
      y: frame.y(a.lat) * VB_HEIGHT,
      r: (NODE_MIN_RADIUS + (NODE_MAX_RADIUS - NODE_MIN_RADIUS) * nodeSizeFraction(visits[i], minVisits, maxVisits)) * s,
    }))
    // Largest first, so a hub's casing never erases the small field beside it.
    const order = [...markers].sort((a, b) => b.r - a.r)

    return {
      landPath: ringsToPath(land.fill, vbWidth, VB_HEIGHT, true),
      coastPath: ringsToPath(land.coast, vbWidth, VB_HEIGHT, false),
      graticulePath,
      legPaths,
      arrowPaths,
      markers: order,
    }
  }, [airports, legs, aspect, vbWidth, s])

  return (
    <div className={['fp-screen__card', 'fp-visited-card', className].filter(Boolean).join(' ')}>
      <span className="fp-screen__card-title fp-type-label-large">{title}</span>

      {globeAvailable && airports.length > 0 && (
        <ModeSelector
          options={[
            { label: 'Flat', contentDescription: 'Flat map, honest about how dense the network is' },
            { label: 'Globe', contentDescription: 'Globe, honest about how far apart the airports are' },
          ]}
          selectedIndex={view}
          onSelect={(i) => setView(i === 1 ? 1 : 0)}
        />
      )}

      {showGlobe ? (
        <div className="fp-visited-card__globe">
          <GlobeNetwork airports={airports} legs={legs} />
        </div>
      ) : scene == null ? (
        <span className="fp-screen__row-detail fp-type-body-medium">
          No flights logged yet.
        </span>
      ) : (
        <svg
          className="fp-visited-network"
          viewBox={`0 0 ${vbWidth} ${VB_HEIGHT}`}
          role="img"
          aria-label={`${airports.length} airports visited`}
        >
          <path
            d={scene.landPath}
            fill="currentColor"
            fillOpacity={WORLD_MAP_LAND_ALPHA}
            fillRule="evenodd"
          />
          {scene.graticulePath !== '' && (
            <path
              d={scene.graticulePath}
              fill="none"
              stroke="currentColor"
              strokeOpacity={WORLD_MAP_LAND_ALPHA}
              strokeWidth={COAST_STROKE * s}
              strokeLinecap="round"
            />
          )}
          <path
            d={scene.coastPath}
            fill="none"
            stroke="currentColor"
            strokeOpacity={WORLD_MAP_COAST_ALPHA}
            strokeWidth={COAST_STROKE * s}
            strokeLinejoin="bevel"
            strokeLinecap="butt"
          />

          {/* Every leg's casing before any leg's line, so where two legs cross
              the network reads as one drawing rather than a stack. */}
          {scene.legPaths.map((d, i) => (
            <path
              key={`casing-${i}`}
              d={d}
              fill="none"
              stroke="var(--fp-surface-container)"
              strokeWidth={ROUTE_STROKE * s + 2 * CASING * s}
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          ))}
          {scene.legPaths.map((d, i) => (
            <path
              key={`route-${i}`}
              d={d}
              fill="none"
              stroke="var(--fp-primary)"
              strokeWidth={ROUTE_STROKE * s}
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          ))}
          {scene.arrowPaths.map((d, i) => (
            <g key={`arrow-${i}`}>
              <path
                d={d}
                fill="none"
                stroke="var(--fp-surface-container)"
                strokeWidth={2 * CASING * s}
                strokeLinejoin="round"
                strokeLinecap="round"
              />
              <path d={d} fill="var(--fp-primary)" />
            </g>
          ))}

          {/* Cased dots, largest first. */}
          {scene.markers.map((m) => (
            <circle key={`node-casing-${m.icao}`} cx={m.x} cy={m.y} r={m.r + CASING * s} fill="var(--fp-surface-container)" />
          ))}
          {scene.markers.map((m) => (
            <circle key={m.icao} cx={m.x} cy={m.y} r={m.r} fill="var(--fp-primary)" />
          ))}
        </svg>
      )}
    </div>
  )
}

function polylineToPath(projected: number[], width: number, height: number): string {
  const parts: string[] = []
  for (let i = 0; i < projected.length / 2; i++) {
    const x = (projected[i * 2] * width).toFixed(2)
    const y = (projected[i * 2 + 1] * height).toFixed(2)
    parts.push(`${i === 0 ? 'M' : 'L'}${x},${y}`)
  }
  return parts.join('')
}

function ringsToPath(rings: ProjectedRings, width: number, height: number, close: boolean): string {
  const parts: string[] = []
  for (let ring = 0; ring < rings.ringStart.length - 1; ring++) {
    const from = rings.ringStart[ring]
    const to = rings.ringStart[ring + 1]
    if (to - from < 2) continue
    for (let i = from; i < to; i++) {
      const x = (rings.points[i * 2] * width).toFixed(2)
      const y = (rings.points[i * 2 + 1] * height).toFixed(2)
      parts.push(`${i === from ? 'M' : 'L'}${x},${y}`)
    }
    if (close) parts.push('Z')
  }
  return parts.join('')
}
