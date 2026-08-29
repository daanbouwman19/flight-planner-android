import { useMemo } from 'react'
import { MapFrame, sampleGeoArc, type ProjectedRings } from '../geo/mapFrame'
import { worldOutline } from '../geo/worldOutline.gen'
import { WORLD_MAP_COAST_ALPHA, WORLD_MAP_LAND_ALPHA } from './RouteMap'

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
  className?: string
}

const VB_HEIGHT = 500

/**
 * Every airport visited, and the legs between them, on one world map.
 *
 * The same projection and the same coastline the route card uses — `MapFrame` over
 * the app's own `land.outline` — so a network drawn here and a route drawn on a
 * card are the same world at different zooms rather than two maps that happen to
 * look alike.
 *
 * The window is fitted to the **visited set** rather than to the whole globe — but
 * only down to `MIN_SPAN_DEGREES`, the same 25° floor `MapFrame` applies to a route.
 * A logbook confined to the Low Countries therefore still shows most of north-west
 * Europe with the fields clustered in the middle of it, and that is deliberate: below
 * about that span the coastline stops being recognisable and the map becomes a
 * featureless rectangle with dots on it. Do not lower the floor for this card alone —
 * the constant belongs to the Kotlin, and a network drawn at a different zoom from a
 * route would stop being the same world.
 */
export function VisitedNetworkCard({
  airports,
  legs = [],
  title = 'Where you have been',
  className,
}: VisitedNetworkCardProps) {
  const aspect = 16 / 10
  const vbWidth = Math.round(VB_HEIGHT * aspect)

  const scene = useMemo(() => {
    if (airports.length === 0) return null

    // The frame is fitted to every visited point at once, which is what makes the
    // card about this logbook rather than about the planet.
    const lats = airports.map((a) => a.lat)
    const lons = airports.map((a) => a.lon)
    const frame = MapFrame.forRoute(lats, lons, aspect)
    const land = frame.projectOutline(worldOutline(), 0.05)

    const arcs = legs.map((leg) => {
      const arc = sampleGeoArc(leg.from[0], leg.from[1], leg.to[0], leg.to[1], 64)
      const projected = frame.project(arc.lats, arc.lons)
      const parts: string[] = []
      for (let i = 0; i < projected.length / 2; i++) {
        const x = (projected[i * 2] * vbWidth).toFixed(2)
        const y = (projected[i * 2 + 1] * VB_HEIGHT).toFixed(2)
        parts.push(`${i === 0 ? 'M' : 'L'}${x},${y}`)
      }
      return parts.join('')
    })

    const markers = airports.map((a) => ({
      icao: a.icao,
      x: frame.x(a.lon) * vbWidth,
      y: frame.y(a.lat) * VB_HEIGHT,
      // Visits scale the radius sub-linearly: a field flown twenty times should
      // read as busier than one flown twice without swamping the map.
      r: 3 + Math.sqrt(Math.max(a.visits ?? 1, 1)) * 1.6,
    }))

    return {
      landPath: ringsToPath(land.fill, vbWidth, true),
      coastPath: ringsToPath(land.coast, vbWidth, false),
      arcs,
      markers,
    }
  }, [airports, legs, aspect, vbWidth])

  return (
    <div className={['fp-screen__card', className].filter(Boolean).join(' ')}>
      <span className="fp-screen__card-title fp-type-label-large">{title}</span>
      {scene == null ? (
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
          <path
            d={scene.coastPath}
            fill="none"
            stroke="currentColor"
            strokeOpacity={WORLD_MAP_COAST_ALPHA}
            strokeWidth={2}
            strokeLinejoin="bevel"
          />
          {scene.arcs.map((d, i) => (
            <path
              key={i}
              d={d}
              fill="none"
              stroke="var(--fp-primary)"
              strokeOpacity={0.45}
              strokeWidth={2}
              strokeLinecap="round"
            />
          ))}
          {scene.markers.map((m) => (
            <circle key={m.icao} cx={m.x} cy={m.y} r={m.r} fill="var(--fp-tertiary)" />
          ))}
        </svg>
      )}
    </div>
  )
}

function ringsToPath(rings: ProjectedRings, width: number, close: boolean): string {
  const parts: string[] = []
  for (let ring = 0; ring < rings.ringStart.length - 1; ring++) {
    const from = rings.ringStart[ring]
    const to = rings.ringStart[ring + 1]
    if (to - from < 2) continue
    for (let i = from; i < to; i++) {
      const x = (rings.points[i * 2] * width).toFixed(2)
      const y = (rings.points[i * 2 + 1] * VB_HEIGHT).toFixed(2)
      parts.push(`${i === from ? 'M' : 'L'}${x},${y}`)
    }
    if (close) parts.push('Z')
  }
  return parts.join('')
}
