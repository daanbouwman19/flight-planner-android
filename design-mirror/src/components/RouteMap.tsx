import { useId, useMemo } from 'react'
import { MapFrame, sampleGeoArc, type ProjectedRings } from '../geo/mapFrame'
import { worldOutline } from '../geo/worldOutline.gen'

/** Land, at 8 % of `onSurface`. */
export const WORLD_MAP_LAND_ALPHA = 0.08
/** Its coast, at 16 %. */
export const WORLD_MAP_COAST_ALPHA = 0.16

const COAST_STROKE = 1
const ROUTE_STROKE = 2.5
const CASING = 1.5
const ARROW_LENGTH = 5
const ARROW_HALF_WIDTH = 0.62
const ENDPOINT_RADIUS = 4
const ENDPOINT_STROKE = 2

/**
 * A margin, so a coast just off the card still contributes the segment that
 * enters it, and a stroke's trimmed end falls outside the visible area.
 */
const OUTLINE_MARGIN = 0.05

export interface RouteMapProps {
  /** Departure latitude, degrees north. */
  depLat: number
  /** Departure longitude, degrees east. */
  depLon: number
  destLat: number
  destLon: number
  /** CSS width. Defaults to `100%`. */
  width?: string
  /**
   * Width divided by height. Defaults to `16 / 10`, the route card's hero.
   *
   * It sets the element's own `aspect-ratio` **and** the window the map is
   * projected through, so the two cannot disagree. A projection computed for one
   * shape and painted into another squashes the land, which is the whole reason
   * the frame takes an aspect ratio in the first place.
   */
  aspect?: number
  className?: string
}

/**
 * The route card's background: real geography, with the leg drawn across it.
 *
 * **It takes coordinates, not a picture.** The window it projects through depends
 * on the canvas aspect ratio, so the projection cannot be done before the card is
 * measured — which is why this is a component and not an asset.
 *
 * Land sits at 8 % of `onSurface` with its coast at 16 %, and the route is then
 * the only saturated thing on the card. **That ratio is what makes a scrim
 * unnecessary**, and a design that needs no scrim is simpler than one hiding
 * behind a gradient — so do not add one over this.
 *
 * Every line is drawn twice, a casing in the card's colour under the line itself,
 * which is the technique a chart uses to keep a route readable wherever it crosses
 * something. Departure is hollow and destination is filled — the chart convention
 * for "from here to there" — and the arrowhead states the direction outright,
 * because the codes are pinned to the card's edges and on a westbound leg the
 * departure code sits on the left while its marker sits on the right.
 *
 * When the window frames no coastline at all, a graticule is drawn instead, so an
 * inland card reads as a place rather than as a failed load.
 *
 * ```tsx
 * <RouteMap depLat={52.31} depLon={4.76} destLat={40.64} destLon={-73.78} />
 * ```
 */
export function RouteMap({
  depLat,
  depLon,
  destLat,
  destLon,
  width = '100%',
  aspect = 16 / 10,
  className,
}: RouteMapProps) {
  // A fixed viewBox rather than a measured pixel size: SVG scales it, and the
  // projection only needs the aspect ratio, which is the thing that actually
  // changes the window.
  const vbHeight = 1000
  const vbWidth = Math.round(vbHeight * aspect)
  const clipId = useId()

  const scene = useMemo(() => {
    const arc = sampleGeoArc(depLat, depLon, destLat, destLon)
    const frame = MapFrame.forRoute(arc.lats, arc.lons, aspect)
    const land = frame.projectOutline(worldOutline(), OUTLINE_MARGIN)
    const projected = frame.project(arc.lats, arc.lons)

    const isEmpty = (r: ProjectedRings) => r.ringStart.length <= 1
    return {
      landPath: ringsToPath(land.fill, vbWidth, vbHeight, true),
      coastPath: ringsToPath(land.coast, vbWidth, vbHeight, false),
      graticulePath: isEmpty(land.coast)
        ? ringsToPath(frame.graticule(), vbWidth, vbHeight, false)
        : '',
      routePath: polylineToPath(projected, vbWidth, vbHeight),
      arrow: arrowPath(projected, Math.floor(projected.length / 4), vbWidth, vbHeight, ARROW_LENGTH * scaleFor(vbHeight)),
      departure: [projected[0] * vbWidth, projected[1] * vbHeight] as const,
      destination: [
        projected[projected.length - 2] * vbWidth,
        projected[projected.length - 1] * vbHeight,
      ] as const,
    }
  }, [depLat, depLon, destLat, destLon, aspect, vbWidth])

  const s = scaleFor(vbHeight)
  const routeWidth = ROUTE_STROKE * s
  const casingWidth = routeWidth + 2 * CASING * s
  const endpointRadius = ENDPOINT_RADIUS * s
  const endpointStroke = ENDPOINT_STROKE * s

  return (
    <svg
      className={['fp-route-map', className].filter(Boolean).join(' ')}
      style={{ width, aspectRatio: String(aspect), display: 'block' }}
      viewBox={`0 0 ${vbWidth} ${vbHeight}`}
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      {/* The map paints past its own bounds by design — see OUTLINE_MARGIN — so
          it crops itself rather than relying on an ancestor to do it.

          **The id is per instance.** A constant one collides the moment two maps
          share a page, and duplicate ids resolve to the first in document order —
          whose rect is sized in `userSpaceOnUse` units from *its* aspect. A route
          detail (vbWidth 1800) above a list of cards (2000) therefore cropped every
          card's right tenth, coastline and destination marker included. */}
      <clipPath id={clipId}>
        <rect x="0" y="0" width={vbWidth} height={vbHeight} />
      </clipPath>
      <g clipPath={`url(#${clipId})`}>
        {/* Even-odd, so a ring enclosed by another — the Caspian, the Great
            Lakes — is a hole rather than more land. */}
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
        {/* Bevel joins and butt caps, where the route below keeps round ones: at
            1 dp and 16 % opacity a round join's arc is sub-pixel, so it is pure
            cost on a few thousand segments. */}
        <path
          d={scene.coastPath}
          fill="none"
          stroke="currentColor"
          strokeOpacity={WORLD_MAP_COAST_ALPHA}
          strokeWidth={COAST_STROKE * s}
          strokeLinejoin="bevel"
          strokeLinecap="butt"
        />

        {/* Casing first, then the line: round joins and caps are what make the
            pair read as one ribbon rather than a stack of segments. */}
        <path
          d={scene.routePath}
          fill="none"
          stroke="var(--fp-surface-container)"
          strokeWidth={casingWidth}
          strokeLinejoin="round"
          strokeLinecap="round"
        />
        <path
          d={scene.routePath}
          fill="none"
          stroke="var(--fp-primary)"
          strokeWidth={routeWidth}
          strokeLinejoin="round"
          strokeLinecap="round"
        />

        {scene.arrow !== '' && (
          <>
            <path
              d={scene.arrow}
              fill="none"
              stroke="var(--fp-surface-container)"
              strokeWidth={2 * CASING * s}
              strokeLinejoin="round"
              strokeLinecap="round"
            />
            <path d={scene.arrow} fill="var(--fp-primary)" />
          </>
        )}

        {/* Departure hollow, destination filled. */}
        <circle
          cx={scene.departure[0]}
          cy={scene.departure[1]}
          r={endpointRadius + CASING * s}
          fill="none"
          stroke="var(--fp-surface-container)"
          strokeWidth={endpointStroke + 2 * CASING * s}
        />
        <circle
          cx={scene.departure[0]}
          cy={scene.departure[1]}
          r={endpointRadius}
          fill="none"
          stroke="var(--fp-primary)"
          strokeWidth={endpointStroke}
        />
        <circle
          cx={scene.destination[0]}
          cy={scene.destination[1]}
          r={endpointRadius + CASING * s}
          fill="var(--fp-surface-container)"
        />
        <circle
          cx={scene.destination[0]}
          cy={scene.destination[1]}
          r={endpointRadius}
          fill="var(--fp-primary)"
        />
      </g>
    </svg>
  )
}

/**
 * Viewbox units per dp.
 *
 * The Android component works in dp against a 220 dp card; the viewBox is 1000
 * tall, so a dp is this many units. Without it every stroke would be hairline.
 */
function scaleFor(vbHeight: number): number {
  return vbHeight / 220
}

function ringsToPath(
  rings: ProjectedRings,
  width: number,
  height: number,
  close: boolean,
): string {
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

function polylineToPath(projected: number[], width: number, height: number): string {
  const parts: string[] = []
  for (let i = 0; i < projected.length / 2; i++) {
    const x = (projected[i * 2] * width).toFixed(2)
    const y = (projected[i * 2 + 1] * height).toFixed(2)
    parts.push(`${i === 0 ? 'M' : 'L'}${x},${y}`)
  }
  return parts.join('')
}

/**
 * The direction arrowhead, at the arc's middle sample.
 *
 * A triangle rather than a rounded polygon: at this size the corner radii round
 * away the tip, which carries the whole signal. It points along the two samples
 * either side of the midpoint, so it follows the curve rather than the straight
 * line between the ends.
 */
function arrowPath(
  projected: number[],
  index: number,
  width: number,
  height: number,
  length: number,
): string {
  const count = projected.length / 2
  if (count < 3 || index <= 0 || index >= count - 1) return ''

  const x = projected[index * 2] * width
  const y = projected[index * 2 + 1] * height
  const dx = (projected[(index + 1) * 2] - projected[(index - 1) * 2]) * width
  const dy = (projected[(index + 1) * 2 + 1] - projected[(index - 1) * 2 + 1]) * height
  const magnitude = Math.sqrt(dx * dx + dy * dy)
  // A route whose midpoint samples coincide has no heading to draw — a departure
  // and destination at the same airport, which the generator can produce.
  if (magnitude < 1e-3) return ''

  const ux = dx / magnitude
  const uy = dy / magnitude
  const px = -uy
  const py = ux
  const half = length * ARROW_HALF_WIDTH

  const p = (a: number, b: number) => `${a.toFixed(2)},${b.toFixed(2)}`
  return (
    `M${p(x + ux * length, y + uy * length)}` +
    `L${p(x - ux * length * 0.4 + px * half, y - uy * length * 0.4 + py * half)}` +
    `L${p(x - ux * length * 0.4 - px * half, y - uy * length * 0.4 - py * half)}Z`
  )
}
