/**
 * The patch of the world one route card shows, and the projection onto it.
 *
 * Ported field-for-field from `MapFrame` in `:core:routing` — the Kotlin is the
 * reference, and every constant here is its constant. It is a port rather than a
 * fresh implementation because the map's two layers have to line up: a coastline
 * normalised to its own extent would sit somewhere else entirely from the route.
 *
 * ### The projection is equirectangular about a standard parallel
 *
 * Plain plate carrée — longitude straight onto x — stretches everything by
 * `1 / cos(latitude)`: at Amsterdam that is 1.6×, enough to make the North Sea
 * look like an ocean and Britain look fat. Scaling longitude by the cosine of the
 * window's centre latitude fixes the shape where the route actually is, which is
 * the only place on a small card anyone is looking. Near the poles that factor
 * collapses, so it is floored.
 */

/** The narrowest window a card will show, in degrees of longitude. */
export const MIN_SPAN_DEGREES = 25.0

/** Fraction of the window kept clear around the route, per side. */
export const PADDING_FRACTION = 0.12

/** Floor on the cosine that scales longitude, at 75° of latitude. */
const MIN_LON_SCALE = 0.2588

const toRadians = (deg: number) => (deg * Math.PI) / 180
const toDegrees = (rad: number) => (rad * 180) / Math.PI

export interface GeoArc {
  /** Degrees north, one entry per sample. */
  lats: number[]
  /** Degrees east. **Unwrapped** — may leave [-180, 180]; see `sampleGeoArc`. */
  lons: number[]
}

/** Interleaved `x, y` fractions with a start offset per ring. */
export interface ProjectedRings {
  points: number[]
  ringStart: number[]
}

export interface ProjectedLand {
  /** Closed polygons, to be filled with an **even-odd** rule so a lake is a hole. */
  fill: ProjectedRings
  /** Open polylines — the real coast, trimmed. Never closed, never filled. */
  coast: ProjectedRings
}

export interface WorldOutline {
  lon: Float32Array
  lat: Float32Array
  ringStart: Int32Array
  ringMinLon: Float32Array
  ringMaxLon: Float32Array
  ringMinLat: Float32Array
  ringMaxLat: Float32Array
}

export class MapFrame {
  constructor(
    /** Centre longitude in degrees east, possibly outside [-180, 180]. */
    readonly centreLon: number,
    readonly centreLat: number,
    /** Degrees of longitude the card's full width covers. */
    readonly spanLon: number,
    readonly spanLat: number,
  ) {
    if (!(spanLon > 0 && spanLat > 0)) {
      throw new Error(`Frame spans must be positive, were ${spanLon} by ${spanLat}`)
    }
  }

  get minLon() {
    return this.centreLon - this.spanLon / 2
  }
  get maxLon() {
    return this.centreLon + this.spanLon / 2
  }
  get minLat() {
    return this.centreLat - this.spanLat / 2
  }
  get maxLat() {
    return this.centreLat + this.spanLat / 2
  }

  /** Fraction of the card's width, 0 at the left edge. */
  x(lon: number): number {
    return (lon - this.minLon) / this.spanLon
  }

  /** Fraction of the card's height, 0 at the **top**, i.e. north. */
  y(lat: number): number {
    return (this.maxLat - lat) / this.spanLat
  }

  /** Projects a sampled arc into interleaved `x, y` fractions. */
  project(lats: number[], lons: number[]): number[] {
    const out: number[] = new Array(lats.length * 2)
    for (let i = 0; i < lats.length; i++) {
      out[i * 2] = this.x(lons[i])
      out[i * 2 + 1] = this.y(lats[i])
    }
    return out
  }

  /**
   * A window around a sampled route, sized to the canvas it will be drawn on.
   *
   * @param aspect canvas width divided by height. The spans are fitted to it so a
   *   degree covers the same number of pixels on both axes and land is not
   *   squashed to the card's shape.
   */
  static forRoute(
    lats: number[],
    lons: number[],
    aspect: number,
    minSpanDegrees = MIN_SPAN_DEGREES,
    paddingFraction = PADDING_FRACTION,
  ): MapFrame {
    if (lats.length === 0 || lats.length !== lons.length) {
      throw new Error(`Need matching non-empty arrays, got ${lats.length} and ${lons.length}`)
    }
    if (!(aspect > 0)) throw new Error(`Canvas aspect must be positive, was ${aspect}`)

    let minLat = lats[0]
    let maxLat = lats[0]
    let minLon = lons[0]
    let maxLon = lons[0]
    for (let i = 1; i < lats.length; i++) {
      if (lats[i] < minLat) minLat = lats[i]
      if (lats[i] > maxLat) maxLat = lats[i]
      if (lons[i] < minLon) minLon = lons[i]
      if (lons[i] > maxLon) maxLon = lons[i]
    }

    const centreLat = (minLat + maxLat) / 2
    const centreLon = (minLon + maxLon) / 2
    const lonScale = Math.max(Math.cos(toRadians(centreLat)), MIN_LON_SCALE)

    const padding = 1 + 2 * paddingFraction
    let width = (maxLon - minLon) * lonScale * padding
    let height = (maxLat - minLat) * padding

    // Fit the canvas by *growing* the deficient axis. Shrinking the other instead
    // would crop the route the frame exists to show.
    if (width < height * aspect) width = height * aspect
    else height = width / aspect

    const floorWidth = minSpanDegrees * lonScale
    if (width <= 0) {
      // Departure and destination at the same point: there is no extent to scale,
      // so the floor is set outright rather than by a ratio that would be infinite.
      width = floorWidth
      height = width / aspect
    } else if (width < floorWidth) {
      const scale = floorWidth / width
      width *= scale
      height *= scale
    }

    return new MapFrame(centreLon, centreLat, width / lonScale, height)
  }

  /**
   * Projects the land this window shows, clipped to it.
   *
   * A ring is rejected by its bounding box first, drawn at **every** whole turn of
   * longitude that reaches the window — Antarctica spans the full range by itself,
   * so a window straddling the seam needs it twice — and what survives is clipped.
   *
   * The fill and the coast are clipped differently on purpose: a polygon clipped
   * to a rectangle has the rectangle's edges in its boundary, which is right for a
   * fill and wrong for a stroke, because stroking it draws a hairline box around
   * every card.
   */
  projectOutline(outline: WorldOutline, margin = 0): ProjectedLand {
    const windowMinLon = this.minLon - margin * this.spanLon
    const windowMaxLon = this.maxLon + margin * this.spanLon
    const windowMinLat = this.minLat - margin * this.spanLat
    const windowMaxLat = this.maxLat + margin * this.spanLat

    const low = -margin
    const high = 1 + margin

    const fill = new RingBuilder()
    const coast = new RingBuilder()
    const ringCount = outline.ringStart.length - 1

    for (let ring = 0; ring < ringCount; ring++) {
      if (outline.ringMaxLat[ring] < windowMinLat) continue
      if (outline.ringMinLat[ring] > windowMaxLat) continue

      const shifts = lonShiftsFor(
        outline.ringMinLon[ring],
        outline.ringMaxLon[ring],
        windowMinLon,
        windowMaxLon,
      )
      if (shifts.length === 0) continue

      const from = outline.ringStart[ring]
      const to = outline.ringStart[ring + 1]

      for (const shift of shifts) {
        const scratch: number[] = []
        for (let i = from; i < to; i++) {
          scratch.push(this.x(outline.lon[i] + shift))
          scratch.push(this.y(outline.lat[i]))
        }
        clipPolygonInto(scratch, low, high, fill)
        clipPolylineInto(scratch, low, high, coast)
      }
    }

    return { fill: fill.build(), coast: coast.build() }
  }

  /**
   * Whole-degree parallels and meridians crossing this window.
   *
   * For the cards that frame no coastline at all — the interior of a continent,
   * the middle of an ocean — where the alternative is a flat wash that reads as a
   * failed load rather than as a place.
   */
  graticule(): ProjectedRings {
    const step = graticuleStep(Math.max(this.spanLon, this.spanLat))
    const points: number[] = []
    const starts: number[] = [0]

    let meridian = Math.ceil(this.minLon / step) * step
    while (meridian <= this.maxLon) {
      points.push(this.x(meridian), 0, this.x(meridian), 1)
      starts.push(points.length / 2)
      meridian += step
    }

    let parallel = Math.ceil(this.minLat / step) * step
    while (parallel <= this.maxLat) {
      // Clamped: a window can extend past the pole, and a parallel there is not a
      // place — drawing it would put a line across the card at a latitude that
      // does not exist.
      if (parallel >= -90 && parallel <= 90) {
        points.push(0, this.y(parallel), 1, this.y(parallel))
        starts.push(points.length / 2)
      }
      parallel += step
    }

    return { points, ringStart: starts }
  }
}

/** A round number of degrees that leaves two to four lines across `span`. */
function graticuleStep(span: number): number {
  if (span > 120) return 45
  if (span > 60) return 20
  if (span > 30) return 10
  return 5
}

/**
 * Which whole turns of longitude bring a ring into the window.
 *
 * Usually one. It is not always one because of Antarctica: it spans −180° to 180°
 * by itself, so a window straddling the seam overlaps it at two different shifts
 * and needs both drawn.
 */
function lonShiftsFor(
  ringMinLon: number,
  ringMaxLon: number,
  windowMinLon: number,
  windowMaxLon: number,
): number[] {
  const found: number[] = []
  for (let turns = -1; turns <= 1; turns++) {
    const shift = turns * 360
    if (ringMaxLon + shift >= windowMinLon && ringMinLon + shift <= windowMaxLon) {
      found.push(shift)
    }
  }
  return found
}

class RingBuilder {
  private points: number[] = []
  private starts: number[] = [0]

  addRing(ring: number[], minimumPoints: number) {
    if (ring.length / 2 < minimumPoints) return
    for (const v of ring) this.points.push(v)
    this.starts.push(this.points.length / 2)
  }

  build(): ProjectedRings {
    return { points: this.points, ringStart: this.starts }
  }
}

/**
 * Sutherland–Hodgman against the box, emitting a **closed** polygon.
 *
 * The classic caveat applies and does not matter here: a ring that leaves and
 * re-enters comes back joined by a zero-width sliver along the edge rather than as
 * two polygons. Filled, that is invisible; only stroking such a boundary shows it.
 */
export function clipPolygonInto(
  ring: number[],
  low: number,
  high: number,
  into: RingBuilder,
): void {
  let source = ring
  for (let edge = 0; edge < 4; edge++) {
    const axis = edge % 2
    const keepAbove = edge < 2
    const bound = keepAbove ? low : high
    const target: number[] = []
    clipHalfPlane(source, axis, bound, keepAbove, target)
    if (target.length === 0) return
    source = target
  }
  into.addRing(source, 3)
}

function clipHalfPlane(
  source: number[],
  axis: number,
  bound: number,
  keepAbove: boolean,
  target: number[],
): void {
  const count = source.length / 2
  if (count === 0) return

  let previousX = source[(count - 1) * 2]
  let previousY = source[(count - 1) * 2 + 1]
  let previousInside = inside(previousX, previousY, axis, bound, keepAbove)

  for (let i = 0; i < count; i++) {
    const currentX = source[i * 2]
    const currentY = source[i * 2 + 1]
    const currentInside = inside(currentX, currentY, axis, bound, keepAbove)

    if (currentInside !== previousInside) {
      const from = axis === 0 ? previousX : previousY
      const to = axis === 0 ? currentX : currentY
      const t = to === from ? 0 : (bound - from) / (to - from)
      target.push(previousX + (currentX - previousX) * t)
      target.push(previousY + (currentY - previousY) * t)
    }
    if (currentInside) {
      target.push(currentX)
      target.push(currentY)
    }

    previousX = currentX
    previousY = currentY
    previousInside = currentInside
  }
}

function inside(x: number, y: number, axis: number, bound: number, keepAbove: boolean): boolean {
  const value = axis === 0 ? x : y
  return keepAbove ? value >= bound : value <= bound
}

/**
 * Liang–Barsky per segment, emitting **open** polylines.
 *
 * The result follows the real coast and never contains an edge of the box, so
 * stroking it cannot draw a frame around the card.
 */
export function clipPolylineInto(
  ring: number[],
  low: number,
  high: number,
  into: RingBuilder,
): void {
  const count = ring.length / 2
  if (count < 2) return

  let run: number[] = []
  const flush = () => {
    into.addRing(run, 2)
    run = []
  }

  for (let i = 0; i < count - 1; i++) {
    const x1 = ring[i * 2]
    const y1 = ring[i * 2 + 1]
    const x2 = ring[(i + 1) * 2]
    const y2 = ring[(i + 1) * 2 + 1]

    const span = clipSegment(x1, y1, x2, y2, low, high)
    if (span === null) {
      flush()
      continue
    }

    const [enter, exit] = span
    if (run.length === 0 || enter > 0) {
      flush()
      run.push(x1 + (x2 - x1) * enter)
      run.push(y1 + (y2 - y1) * enter)
    }
    run.push(x1 + (x2 - x1) * exit)
    run.push(y1 + (y2 - y1) * exit)
    if (exit < 1) flush()
  }
  flush()
}

function clipSegment(
  x1: number,
  y1: number,
  x2: number,
  y2: number,
  low: number,
  high: number,
): [number, number] | null {
  const dx = x2 - x1
  const dy = y2 - y1
  let enter = 0
  let exit = 1

  const directions = [-dx, dx, -dy, dy]
  const distances = [x1 - low, high - x1, y1 - low, high - y1]

  for (let side = 0; side < 4; side++) {
    const direction = directions[side]
    const distance = distances[side]
    if (direction === 0) {
      // Parallel to this edge: inside or outside for its whole length.
      if (distance < 0) return null
      continue
    }
    const t = distance / direction
    if (direction < 0) {
      if (t > exit) return null
      if (t > enter) enter = t
    } else {
      if (t < enter) return null
      if (t < exit) exit = t
    }
  }
  return [enter, exit]
}

/** The signed shortest way round from `from` to `to`, in degrees. */
function longitudeDelta(from: number, to: number): number {
  let delta = (to - from) % 360
  if (delta > 180) delta -= 360
  if (delta < -180) delta += 360
  return delta
}

const DEGENERATE_RADIANS = 1e-9

/**
 * Samples the great circle between two points.
 *
 * Longitudes come back **unwrapped**: a Tokyo–Los Angeles route runs from 140° up
 * past 180° to 242°, because removing the seam is what stops the card drawing a
 * line back across the planet. `MapFrame` keeps that convention and shifts land
 * into the window by whole turns instead.
 *
 * @param samples 128 for a card-sized map; the Android default of 24 is sized for
 *   a sparkline and shows its chords at card size.
 */
export function sampleGeoArc(
  depLat: number,
  depLon: number,
  destLat: number,
  destLon: number,
  samples = 128,
): GeoArc {
  const count = Math.max(samples, 2)
  const lats: number[] = new Array(count)
  const lons: number[] = new Array(count)

  const lat1 = toRadians(depLat)
  const lon1 = toRadians(depLon)
  const lat2 = toRadians(destLat)
  const lon2 = toRadians(destLon)

  const cosLat1 = Math.cos(lat1)
  const cosLat2 = Math.cos(lat2)
  const x1 = cosLat1 * Math.cos(lon1)
  const y1 = cosLat1 * Math.sin(lon1)
  const z1 = Math.sin(lat1)
  const x2 = cosLat2 * Math.cos(lon2)
  const y2 = cosLat2 * Math.sin(lon2)
  const z2 = Math.sin(lat2)

  const dot = Math.min(Math.max(x1 * x2 + y1 * y2 + z1 * z2, -1), 1)
  const omega = Math.atan2(Math.sqrt(1 - dot * dot), dot)
  const sinOmega = Math.sin(omega)
  // Coincident and antipodal both leave sin(omega) at zero, where the slerp
  // weights divide by nothing useful. Linear interpolation is the honest fallback.
  const slerpable = omega > DEGENERATE_RADIANS && Math.abs(sinOmega) > DEGENERATE_RADIANS

  for (let i = 0; i < count; i++) {
    const f = i / (count - 1)
    if (slerpable) {
      const a = Math.sin((1 - f) * omega) / sinOmega
      const b = Math.sin(f * omega) / sinOmega
      const px = a * x1 + b * x2
      const py = a * y1 + b * y2
      const pz = a * z1 + b * z2
      lats[i] = toDegrees(Math.atan2(pz, Math.sqrt(px * px + py * py)))
      lons[i] = toDegrees(Math.atan2(py, px))
    } else {
      lats[i] = depLat + (destLat - depLat) * f
      lons[i] = depLon + longitudeDelta(depLon, destLon) * f
    }
  }

  // Unwrap, so the seam is removed rather than drawn across.
  for (let i = 1; i < count; i++) {
    lons[i] = lons[i - 1] + longitudeDelta(lons[i - 1], lons[i])
  }

  return { lats, lons }
}

/** Great-circle distance in nautical miles. */
export function distanceNm(
  depLat: number,
  depLon: number,
  destLat: number,
  destLon: number,
): number {
  const dLat = toRadians(destLat - depLat)
  const dLon = toRadians(destLon - depLon)
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRadians(depLat)) * Math.cos(toRadians(destLat)) * Math.sin(dLon / 2) ** 2
  return 2 * Math.asin(Math.min(1, Math.sqrt(a))) * 3440.065
}
