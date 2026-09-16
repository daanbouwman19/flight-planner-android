/**
 * The globe's camera and its opening fit — the sphere seen from outside.
 *
 * Ported field-for-field from `:feature:globe`'s `math/GlobeCamera.kt` and
 * `math/GlobeFit.kt`, which are themselves a port of the Rust desktop app's
 * `camera.rs`. The Kotlin is the behavioural reference; every constant here is
 * its constant.
 *
 * ### What the mirror does and does not carry
 *
 * The app draws NASA satellite imagery on a Filament sphere. Claude Design's
 * runtime is SVG and CSS with no GPU and no imagery asset, so the mirror draws
 * the **same `land.outline` the phone reads**, through this projection, clipped
 * to the limb — an orthographic-looking outline globe rather than a photograph.
 * That is a recorded divergence, the same kind `SkyProfile` makes for motion.
 *
 * What *is* faithful: the framing (`frameRoute` / `framePoints`, aspect-aware
 * exactly as the app's `GlobeFit` is), the great-circle arc bending over the
 * curve, the projected DEP/DEST plate anchors, the limb silhouette, and the
 * back-face cull that hides the far side.
 *
 * The mirror is a **still** globe: there are no gestures, so `bearing` and
 * `tilt` are always zero. The parameters are kept in the signature so the port
 * reads against the Kotlin, but nothing here ever sets them.
 */

/** Vertical field of view: 60 degrees. `DEFAULT_FOV_Y` in the Kotlin. */
export const GLOBE_FOV_Y = Math.PI / 3

/** Camera just above the surface at this distance from the globe centre. */
const MIN_DISTANCE = 1.0001
/** Whole globe with breathing room — the value `distanceToFit` saturates at. */
const MAX_DISTANCE = 10.0

/**
 * The window the opening fit is allowed to settle in — narrower than the app's
 * `[MIN_DISTANCE, MAX_DISTANCE]`, and the mirror's one real divergence from
 * `GlobeFit`.
 *
 * The app's fit runs the full range because the globe is **interactive**: a leg
 * framed near the surface can be pinched out of, a whole-planet logbook can be
 * spun to see the far side. The mirror is a still image, so a fit at either
 * extreme is just what the reader gets — and at `distance ≈ 1.03` a short leg is
 * a near-flat close-up that does not read as a sphere, while at `distance = 10` a
 * hemisphere-spanning logbook is a marble adrift in a field of backdrop. Both
 * defeat the point of showing a globe at all.
 *
 * `[2.2, 4.5]` keeps the disc between "fills the frame, clearly curved" and
 * "a ball with room around it". The floor also stands in for the app's
 * `sharpestAltitude` (one screen pixel per deepest texel), which the mirror has
 * no tiles to compute.
 */
const MIN_FIT_DISTANCE = 2.2
const MAX_FIT_DISTANCE = 4.5

/**
 * How much of each half-axis the fit keeps clear of the outermost point.
 *
 * `EDGE_MARGIN` in `GlobeFit` is `0.18` — "about a plate" on a 411 dp phone
 * window. The mirror's globe boxes are smaller (a 360 dp hero, a 340 dp Stats
 * band), so a plate is a larger *fraction* of the half-width; `0.24` keeps the
 * DEP/DEST plates off the edge here. Same intent, scaled to a smaller box.
 */
const EDGE_MARGIN = 0.24

const DEG = Math.PI / 180
const RAD = 180 / Math.PI
const HALF_PI = Math.PI / 2
const AXIS_EPSILON = 1e-4
const DEGENERATE_MEAN = 1e-4

export interface Vec3 {
  x: number
  y: number
  z: number
}

const v = (x: number, y: number, z: number): Vec3 => ({ x, y, z })
const add = (a: Vec3, b: Vec3): Vec3 => v(a.x + b.x, a.y + b.y, a.z + b.z)
const sub = (a: Vec3, b: Vec3): Vec3 => v(a.x - b.x, a.y - b.y, a.z - b.z)
const scale = (a: Vec3, s: number): Vec3 => v(a.x * s, a.y * s, a.z * s)
const dot = (a: Vec3, b: Vec3): number => a.x * b.x + a.y * b.y + a.z * b.z
const cross = (a: Vec3, b: Vec3): Vec3 =>
  v(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)
const length = (a: Vec3): number => Math.sqrt(dot(a, a))
const normalize = (a: Vec3): Vec3 => {
  const l = length(a)
  return l < 1e-12 ? v(0, 0, 1) : scale(a, 1 / l)
}
const clamp = (x: number, lo: number, hi: number): number => Math.min(Math.max(x, lo), hi)

/** The unit-sphere point at `latDeg` / `lonDeg`. `latLonToWorld` in the Kotlin. */
export function latLonToWorld(latDeg: number, lonDeg: number): Vec3 {
  const lat = latDeg * DEG
  const lon = lonDeg * DEG
  const cosLat = Math.cos(lat)
  return v(cosLat * Math.sin(lon), Math.sin(lat), cosLat * Math.cos(lon))
}

/** A projected screen point, in the viewBox units the caller passed as the viewport. */
export interface ScreenPoint {
  x: number
  y: number
}

/** The camera's world-space basis, `computeBasis()` in the Kotlin (bearing = tilt = 0). */
interface CameraBasis {
  right: Vec3
  up: Vec3
  look: Vec3
  position: Vec3
  /** `position / |position|` — the back-face test is one dot product against this. */
  facingUnit: Vec3
}

/**
 * Where the globe is being looked at from, as the same five numbers `GlobeCamera`
 * carries. `bearing` and `tilt` exist for parity with the port and are always 0.
 */
export class GlobeCamera {
  constructor(
    readonly centerLat = 0,
    readonly centerLon = 0,
    /** Camera height above the unit-sphere surface. `distance` is `1 + altitude`. */
    readonly altitude = 1,
    readonly bearing = 0,
    readonly tilt = 0,
    readonly fovY = GLOBE_FOV_Y,
  ) {}

  get distance(): number {
    return 1 + this.altitude
  }

  /** Focal length in viewport units: half the height over `tan(fovY / 2)`. */
  focal(viewportHeight: number): number {
    return viewportHeight * 0.5 / Math.tan(this.fovY * 0.5)
  }

  private _basis: CameraBasis | null = null

  /** The camera basis, computed once — a projection pass reuses it per vertex. */
  private basis(): CameraBasis {
    if (this._basis) return this._basis
    this._basis = this.computeBasis()
    return this._basis
  }

  private computeBasis(): CameraBasis {
    const lat = this.centerLat * DEG
    const lon = this.centerLon * DEG
    const sLat = Math.sin(lat)
    const cLat = Math.cos(lat)
    const sLon = Math.sin(lon)
    const cLon = Math.cos(lon)

    // Nadir, and the north / east tangents at it.
    const n = v(cLat * sLon, sLat, cLat * cLon)
    const northN = v(-sLat * sLon, cLat, -sLat * cLon)
    const eastN = v(cLon, 0, -sLon)

    const sB = Math.sin(this.bearing)
    const cB = Math.cos(this.bearing)
    const upBase = add(scale(northN, cB), scale(eastN, sB))
    const right = normalize(cross(upBase, n))

    const sT = Math.sin(this.tilt)
    const cT = Math.cos(this.tilt)
    const look = normalize(add(scale(n, -cT), scale(upBase, sT)))
    const up = normalize(add(scale(upBase, cT), scale(n, sT)))

    const r = this.distance
    const t = -cT + Math.sqrt(Math.max(0, r * r - sT * sT))
    const position = add(scale(n, 1 + t * cT), scale(upBase, -t * sT))

    return { right, up, look, position, facingUnit: scale(position, 1 / r) }
  }

  /**
   * Perspective-project a world point. Null when the point is behind the camera
   * plane — the caller must treat that as "no position", not as off-screen.
   */
  worldToScreen(world: Vec3, viewport: GlobeViewport): ScreenPoint | null {
    const b = this.basis()
    const d = sub(world, b.position)
    const cam = v(dot(d, b.right), dot(d, b.up), dot(d, b.look))
    if (cam.z <= 1e-6) return null
    const f = this.focal(viewport.height)
    return {
      x: viewport.width * 0.5 + (f * cam.x) / cam.z,
      y: viewport.height * 0.5 - (f * cam.y) / cam.z,
    }
  }

  /** `dot(world, position / |position|)`. Above `1 / distance` the point faces us. */
  facingValue(world: Vec3): number {
    return dot(world, this.basis().facingUnit)
  }

  /** The facing value at the geometric horizon. */
  get horizon(): number {
    return 1 / this.distance
  }

  /**
   * The limb silhouette as a screen-space circle (true for `tilt = 0`, which the
   * mirror always is). Returned as centre and radius in viewport units.
   */
  limbCircle(viewport: GlobeViewport): { cx: number; cy: number; r: number } {
    const cx = viewport.width * 0.5
    const cy = viewport.height * 0.5
    const alphaH = Math.acos(clamp(1 / this.distance, -1, 1))
    // A point sitting exactly on the horizon, in the east tangent direction.
    const lat = this.centerLat * DEG
    const lon = this.centerLon * DEG
    const n = v(Math.cos(lat) * Math.sin(lon), Math.sin(lat), Math.cos(lat) * Math.cos(lon))
    const east = v(Math.cos(lon), 0, -Math.sin(lon))
    const p = add(scale(n, Math.cos(alphaH)), scale(east, Math.sin(alphaH)))
    const s = this.worldToScreen(p, viewport)
    const r = s ? Math.hypot(s.x - cx, s.y - cy) : Math.min(cx, cy)
    return { cx, cy, r }
  }
}

/** The box the globe is drawn into, in the caller's own units (an SVG viewBox). */
export interface GlobeViewport {
  width: number
  height: number
}

/**
 * The camera that frames the leg from departure to destination in `viewport`,
 * north up and top down. `GlobeFit.frameRoute`, which is `framePoints` over the
 * two ends and nothing else.
 */
export function frameRoute(
  depLat: number,
  depLon: number,
  destLat: number,
  destLon: number,
  viewport: GlobeViewport,
): GlobeCamera {
  return framePoints([depLat, destLat], [depLon, destLon], viewport)
}

/**
 * The camera that frames every one of `lats` / `lons` in `viewport`.
 *
 * The centre is the **normalised sum of the unit vectors** — for two points that
 * is the midpoint of the great circle between them. The distance is the furthest
 * any point needs, so a set confined to one region zooms in and one spanning two
 * hemispheres pulls back to the whole planet.
 *
 * ### The one divergence from `GlobeFit`
 *
 * The fitted distance is clamped to {@link MIN_FIT_DISTANCE}..{@link
 * MAX_FIT_DISTANCE} rather than the app's full `[1.0001, 10]`. See those
 * constants — the short version is that a still globe has to read as a globe at
 * both ends of the range, where an interactive one can be pinched or spun.
 */
export function framePoints(
  lats: number[],
  lons: number[],
  viewport: GlobeViewport,
): GlobeCamera {
  if (lats.length === 0 || viewport.height < 1 || viewport.width < 1) return new GlobeCamera()

  let sum = v(0, 0, 0)
  for (let i = 0; i < lats.length; i++) sum = add(sum, latLonToWorld(lats[i], lons[i]))

  const centre =
    length(sum) < DEGENERATE_MEAN ? latLonToWorld(lats[0], lons[0]) : normalize(sum)

  const { east, north } = tangentFrame(centre)

  let distance = MIN_DISTANCE
  for (let i = 0; i < lats.length; i++) {
    const p = latLonToWorld(lats[i], lons[i])
    const alpha = Math.acos(clamp(dot(centre, p), -1, 1))
    const tangential = sub(p, scale(centre, dot(centre, p)))
    const phi =
      length(tangential) < DEGENERATE_MEAN
        ? 0
        : Math.atan2(dot(tangential, east), dot(tangential, north))
    distance = Math.max(distance, distanceToFit(alpha, phi, viewport))
  }

  const altitude = clamp(distance, MIN_FIT_DISTANCE, MAX_FIT_DISTANCE) - 1
  return new GlobeCamera(
    Math.asin(clamp(centre.y, -1, 1)) * RAD,
    Math.atan2(centre.x, centre.z) * RAD,
    altitude,
  )
}

/**
 * The distance at which a point `alpha` radians from the centre of the disc,
 * lying in screen direction `phi`, sits just inside the margin. `distanceToFit`
 * in the Kotlin, both regimes.
 */
function distanceToFit(alpha: number, phi: number, viewport: GlobeViewport): number {
  const focal = (viewport.height * 0.5) / Math.tan(GLOBE_FOV_Y * 0.5)
  const horizontal = Math.abs(Math.sin(phi))
  const vertical = Math.abs(Math.cos(phi))
  const radiusPx = Math.min(
    horizontal > AXIS_EPSILON
      ? (viewport.width * 0.5 * (1 - EDGE_MARGIN)) / horizontal
      : Number.MAX_VALUE,
    vertical > AXIS_EPSILON
      ? (viewport.height * 0.5 * (1 - EDGE_MARGIN)) / vertical
      : Number.MAX_VALUE,
  )
  const beta = Math.atan(radiusPx / focal)
  if (beta < AXIS_EPSILON) return MAX_DISTANCE
  if (alpha + beta <= HALF_PI) return Math.sin(alpha + beta) / Math.sin(beta)
  if (alpha < HALF_PI) return 1 / Math.cos(alpha)
  return MAX_DISTANCE
}

/** East and north unit tangents at `centre`. `tangentFrame` in the Kotlin. */
function tangentFrame(centre: Vec3): { east: Vec3; north: Vec3 } {
  const cosLat = Math.sqrt(Math.max(0, 1 - centre.y * centre.y))
  if (cosLat < AXIS_EPSILON) return { east: v(1, 0, 0), north: v(0, 0, 1) }
  const sinLon = centre.x / cosLat
  const cosLon = centre.z / cosLat
  const sinLat = centre.y
  return {
    east: v(cosLon, 0, -sinLon),
    north: v(-sinLat * sinLon, cosLat, -sinLat * cosLon),
  }
}

export interface WorldOutlineLike {
  lon: Float32Array
  lat: Float32Array
  ringStart: Int32Array
  ringMinLon: Float32Array
  ringMaxLon: Float32Array
  ringMinLat: Float32Array
  ringMaxLat: Float32Array
}

/**
 * The world's coastline rings projected onto the globe, each broken wherever it
 * crosses the limb so a stroke never runs across the hidden face.
 *
 * Returned as SVG path data strings — one `M…L…` run per visible fragment.
 */
export function projectOutline(
  outline: WorldOutlineLike,
  camera: GlobeCamera,
  viewport: GlobeViewport,
): { fill: string; coast: string } {
  const horizon = camera.horizon
  const fill: string[] = []
  const coast: string[] = []
  const ringCount = outline.ringStart.length - 1

  for (let ring = 0; ring < ringCount; ring++) {
    const from = outline.ringStart[ring]
    const to = outline.ringStart[ring + 1]
    if (to - from < 3) continue

    // A whole ring on the far side never contributes — a cheap reject before the
    // per-vertex trig.
    const midLon = (outline.ringMinLon[ring] + outline.ringMaxLon[ring]) * 0.5
    const midLat = (outline.ringMinLat[ring] + outline.ringMaxLat[ring]) * 0.5
    const centreFacing = camera.facingValue(latLonToWorld(midLat, midLon))
    const span = Math.max(
      outline.ringMaxLon[ring] - outline.ringMinLon[ring],
      outline.ringMaxLat[ring] - outline.ringMinLat[ring],
    )
    if (centreFacing < -0.2 && span < 90) continue

    let run: string[] = []
    const flush = (close: boolean) => {
      if (run.length >= 2) {
        coast.push(run.join(''))
        if (close && run.length >= 3) fill.push(run.join('') + 'Z')
      }
      run = []
    }

    for (let i = from; i < to; i++) {
      const world = latLonToWorld(outline.lat[i], outline.lon[i])
      if (camera.facingValue(world) <= horizon) {
        flush(false)
        continue
      }
      const p = camera.worldToScreen(world, viewport)
      if (!p) {
        flush(false)
        continue
      }
      run.push(`${run.length === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`)
    }
    // A ring that stayed entirely on the near face closes into a fillable polygon.
    flush(to - from > 0 && run.length >= 3)
  }

  return { fill: fill.join(''), coast: coast.join('') }
}

/**
 * A sampled arc projected onto the globe, broken at the limb.
 *
 * Feed it the unwrapped great-circle samples `sampleGeoArc` already produces.
 */
export function projectArc(
  lats: number[],
  lons: number[],
  camera: GlobeCamera,
  viewport: GlobeViewport,
): string {
  const horizon = camera.horizon
  const parts: string[] = []
  let started = false
  for (let i = 0; i < lats.length; i++) {
    const world = latLonToWorld(lats[i], lons[i])
    if (camera.facingValue(world) <= horizon) {
      started = false
      continue
    }
    const p = camera.worldToScreen(world, viewport)
    if (!p) {
      started = false
      continue
    }
    parts.push(`${started ? 'L' : 'M'}${p.x.toFixed(1)},${p.y.toFixed(1)}`)
    started = true
  }
  return parts.join('')
}

/** Where one lat/lon lands on the glass, and whether it is on the near face. */
export function projectPoint(
  latDeg: number,
  lonDeg: number,
  camera: GlobeCamera,
  viewport: GlobeViewport,
): { x: number; y: number; facing: number; visible: boolean } | null {
  const world = latLonToWorld(latDeg, lonDeg)
  const facing = camera.facingValue(world)
  const p = camera.worldToScreen(world, viewport)
  if (!p) return null
  return { x: p.x, y: p.y, facing, visible: facing > camera.horizon }
}
