/**
 * Runway diagram geometry, ported from `RunwayDiagram.kt` and `SurfaceWind.kt`.
 *
 * The diagram has **two layouts**, and which one it uses is a property of the
 * data rather than a setting. OurAirports publishes real threshold coordinates
 * for every end of most well-documented fields and for very few small ones, so:
 *
 *  - every diagrammed end has a position → `positionedRays` projects them onto a
 *    small local plane and draws each physical runway as the real segment between
 *    its two thresholds: a genuine miniature of Denver or Schiphol;
 *  - otherwise → `layoutRunways` falls back to the compass schematic, one ray per
 *    end at its true heading, with roughly parallel families fanned into evenly
 *    spaced lanes.
 *
 * The fan is not decoration. A first version drew every ray through one shared
 * centre and turned Edwards Air Force Base's three near-parallel runways into a
 * starburst through a point that does not exist.
 */

export interface Runway {
  /** The end's designator — `09L`, `27R`, `18`. */
  ident: string
  /** Length in feet. Both ends of one strip report the same length. */
  lengthFt: number
  /** Width in feet. Ends at or above 100 ft draw with the wide stroke. */
  widthFt?: number
  /** True heading in degrees. An end without one is not diagrammable. */
  trueHeadingDeg?: number
  /** Threshold latitude, when published. */
  latitude?: number
  /** Threshold longitude, when published. */
  longitude?: number
  /** A hard surface draws in the strong ink, a soft one in the variant. */
  hardSurface?: boolean
  /** Whether the end has runway lighting. */
  lit?: boolean
}

export interface Point {
  x: number
  y: number
}

export interface RunwayRay {
  origin: Point
  tip: Point
  /**
   * Where the designator goes.
   *
   * **Getting this wrong is actively misleading rather than untidy.** Both layouts
   * tip a ray at the end the runway *points toward*, and an earlier version
   * anchored the ident there — so every designator sat at the wrong end. A reader
   * then saw `30` at the north-west end, applied the convention every FAA and
   * Jeppesen plate uses, and concluded that departing 30 meant rolling south-east:
   * downwind, and the opposite of what the wind actually recommended.
   */
  threshold: Point
}

/** Metres per degree of latitude — constant enough at any one airport's scale. */
const METERS_PER_DEGREE_LAT = 111_320.0

/** Physical runways closer together than this, by folded heading, share a lane family. */
const PARALLEL_BAND_DEG = 45.0
/** Ends within this of exactly 180° apart are considered the same physical strip. */
const OPPOSITE_TOLERANCE_DEG = 3.0
const LENGTH_TOLERANCE_FRACTION = 0.05
const MIN_LENGTH_TOLERANCE_FT = 50
/** The shortest a ray may draw, as a fraction of the longest runway's length. */
const MIN_LENGTH_FRACTION = 0.35

const toRadians = (deg: number) => (deg * Math.PI) / 180

/**
 * A local, small-scale projection around a reference point: equirectangular, with
 * longitude compressed by the cosine of the reference latitude.
 *
 * An airport's own extent is at most a few kilometres — far too small for the
 * earth's curvature to matter — so a great-circle projection would be doing work
 * this never needs. Units are metres, +x east and **+y south**, matching the
 * screen's own y-down convention so the caller needs no second flip.
 */
export function projectLocal(
  lat: number,
  lon: number,
  refLat: number,
  refLon: number,
): Point {
  const x = (lon - refLon) * METERS_PER_DEGREE_LAT * Math.cos(toRadians(refLat))
  const y = (lat - refLat) * METERS_PER_DEGREE_LAT
  return { x, y: -y }
}

/** A heading folded onto [0, 180) — opposite ends of one strip fold to the same value. */
export function foldToHalfCircle(headingDeg: number): number {
  const folded = headingDeg % 180
  return folded < 0 ? folded + 180 : folded
}

/** The shortest way from `b` to `a` on a circle, in degrees, always non-negative. */
function angularDifference(a: number, b: number): number {
  const diff = ((((a - b) % 360) + 540) % 360) - 180
  return Math.abs(diff)
}

export interface PhysicalRunway {
  runwayIndices: number[]
  orientationDeg: number
}

function lengthTolerance(lengthFt: number): number {
  return Math.max(MIN_LENGTH_TOLERANCE_FT, Math.floor(lengthFt * LENGTH_TOLERANCE_FRACTION))
}

/**
 * Groups the ends into physical strips.
 *
 * Pairing is by **opposite heading and matching length**, never by parsing `09`
 * against `27` or `18R` against `36L` — those conventions are broken often enough
 * in the real dataset that reading them would be guessing.
 */
export function pairPhysicalRunways(runways: Runway[]): PhysicalRunway[] {
  const used = new Array(runways.length).fill(false)
  const groups: PhysicalRunway[] = []

  for (let i = 0; i < runways.length; i++) {
    if (used[i]) continue
    used[i] = true
    const headingI = runways[i].trueHeadingDeg!
    const candidates: number[] = []
    for (let j = i + 1; j < runways.length; j++) {
      if (used[j]) continue
      const headingJ = runways[j].trueHeadingDeg!
      const opposite = angularDifference(headingI + 180, headingJ) < OPPOSITE_TOLERANCE_DEG
      const tolerance = lengthTolerance(Math.min(runways[i].lengthFt, runways[j].lengthFt))
      const sameLength = Math.abs(runways[i].lengthFt - runways[j].lengthFt) <= tolerance
      if (opposite && sameLength) candidates.push(j)
    }

    let partner = -1
    if (candidates.length === 1) partner = candidates[0]
    else if (candidates.length > 1) {
      // Once heading and length leave more than one candidate, the real bearing
      // between the two thresholds settles it.
      //
      // **Seeded with the first candidate rather than with an empty maximum**, so
      // that an all-equal field still pairs. `bearingDeviation` returns
      // `MAX_VALUE` whenever either end lacks a published threshold, which is
      // exactly the dataset that falls back to the lane schematic — a strict
      // `<` against an initial `MAX_VALUE` then never fires, leaves `partner`
      // at -1, and fans 09L/09R/27L/27R into four one-ended lanes instead of two
      // strips. Kotlin's `minByOrNull` picks the first on a tie and its KDoc says
      // so deliberately; this is that behaviour.
      partner = candidates[0]
      let best = bearingDeviation(runways[i], runways[partner], headingI)
      for (const j of candidates.slice(1)) {
        const deviation = bearingDeviation(runways[i], runways[j], headingI)
        if (deviation < best) {
          best = deviation
          partner = j
        }
      }
    }

    if (partner >= 0) {
      used[partner] = true
      groups.push({ runwayIndices: [i, partner], orientationDeg: foldToHalfCircle(headingI) })
    } else {
      groups.push({ runwayIndices: [i], orientationDeg: foldToHalfCircle(headingI) })
    }
  }
  return groups
}

/**
 * How far `to`'s real bearing from `from` departs from `headingDeg`.
 *
 * `MAX_VALUE` when either end lacks coordinates, so an undecidable candidate
 * sorts last rather than winning a tie it cannot actually settle.
 */
function bearingDeviation(from: Runway, to: Runway, headingDeg: number): number {
  if (
    from.latitude == null ||
    from.longitude == null ||
    to.latitude == null ||
    to.longitude == null
  ) {
    return Number.MAX_VALUE
  }
  const φ1 = toRadians(from.latitude)
  const φ2 = toRadians(to.latitude)
  const Δλ = toRadians(to.longitude - from.longitude)
  const y = Math.sin(Δλ) * Math.cos(φ2)
  const x = Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ)
  const bearing = ((Math.atan2(y, x) * 180) / Math.PI + 360) % 360
  return angularDifference(headingDeg, bearing)
}

/**
 * A lane offset per end, as a multiple of the diagram's lane spacing.
 *
 * Both ends of one physical strip always share an offset, so they draw as one
 * continuous, merely-displaced line. A family of one needs no lane and stays at 0,
 * which is what keeps two runways whose headings genuinely differ — and which
 * therefore plausibly cross in reality — drawn through the shared centre.
 */
export function layoutRunways(runways: Runway[]): number[] {
  const offsets = new Array(runways.length).fill(0)
  if (runways.length <= 1) return offsets

  const groups = pairPhysicalRunways(runways)
  if (groups.length <= 1) return offsets

  const order = groups.map((_, i) => i).sort((a, b) => groups[a].orientationDeg - groups[b].orientationDeg)
  let i = 0
  while (i < order.length) {
    let j = i
    while (
      j + 1 < order.length &&
      groups[order[j + 1]].orientationDeg - groups[order[i]].orientationDeg < PARALLEL_BAND_DEG
    ) {
      j++
    }
    const laneCount = j - i + 1
    if (laneCount > 1) {
      for (let rank = i; rank <= j; rank++) {
        // Deliberately fractional: a family of two lanes sits at ∓0.5 so the pair
        // straddles the centre. Rounding here would push both to one side.
        const lane = rank - i - (laneCount - 1) / 2
        for (const index of groups[order[rank]].runwayIndices) offsets[index] = lane
      }
    }
    i = j + 1
  }
  return offsets
}

/**
 * A point `lengthFt` (as a fraction of `maxLengthFt`, floored) along `headingDeg`
 * from `origin` — 0° is up (true north), clockwise.
 */
function extend(
  origin: Point,
  headingDeg: number,
  radius: number,
  lengthFt: number,
  maxLengthFt: number,
): Point {
  const angle = toRadians(headingDeg)
  const dirX = Math.sin(angle)
  const dirY = -Math.cos(angle)
  const fraction =
    maxLengthFt > 0
      ? Math.min(Math.max(lengthFt / maxLengthFt, MIN_LENGTH_FRACTION), 1)
      : 1
  const length = radius * fraction
  return { x: origin.x + dirX * length, y: origin.y + dirY * length }
}

/** The true relative layout, drawn threshold to threshold. */
export function positionedRays(
  runways: Runway[],
  groups: PhysicalRunway[],
  center: Point,
  radius: number,
  maxLengthFt: number,
): RunwayRay[] {
  const refLat = runways.reduce((s, r) => s + r.latitude!, 0) / runways.length
  const refLon = runways.reduce((s, r) => s + r.longitude!, 0) / runways.length
  const local = runways.map((r) => projectLocal(r.latitude!, r.longitude!, refLat, refLon))

  const maxExtent = Math.max(...local.map((p) => Math.hypot(p.x, p.y)))
  const scale = maxExtent > 0 ? radius / maxExtent : 1
  const toScreen = (p: Point): Point => ({
    x: center.x + p.x * scale,
    y: center.y + p.y * scale,
  })

  const rays: (RunwayRay | null)[] = new Array(runways.length).fill(null)
  for (const group of groups) {
    if (group.runwayIndices.length === 2) {
      const [i, j] = group.runwayIndices
      const a = toScreen(local[i])
      const b = toScreen(local[j])
      // Each end's own published coordinate is its threshold, so the ray runs
      // threshold-to-threshold and the designator goes at the origin.
      rays[i] = { origin: a, tip: b, threshold: a }
      rays[j] = { origin: b, tip: a, threshold: b }
    } else {
      const i = group.runwayIndices[0]
      const origin = toScreen(local[i])
      rays[i] = {
        origin,
        tip: extend(origin, runways[i].trueHeadingDeg!, radius, runways[i].lengthFt, maxLengthFt),
        threshold: origin,
      }
    }
  }
  return rays as RunwayRay[]
}

/**
 * One end's ray in the compass schematic.
 *
 * The lane offset is applied perpendicular to the heading **folded to a half
 * circle**, not to the heading itself, so both ends of one strip — whose headings
 * are ~180° apart — displace to the same point rather than in opposite directions.
 *
 * `paired` decides where the designator goes, and **the mirror is wrong without
 * it**: an unpaired end's mirror lands on empty canvas on the far side of the
 * origin, and the label ends up detached from its own line.
 */
export function laneRay(
  runway: Runway,
  laneOffsetPx: number,
  center: Point,
  radius: number,
  maxLengthFt: number,
  paired: boolean,
): RunwayRay {
  const heading = runway.trueHeadingDeg!
  const foldedRad = toRadians(foldToHalfCircle(heading))
  const perpX = Math.cos(foldedRad)
  const perpY = Math.sin(foldedRad)
  const origin = { x: center.x + perpX * laneOffsetPx, y: center.y + perpY * laneOffsetPx }
  const tip = extend(origin, heading, radius, runway.lengthFt, maxLengthFt)
  return {
    origin,
    tip,
    threshold: paired ? { x: origin.x * 2 - tip.x, y: origin.y * 2 - tip.y } : origin,
  }
}

// ---------------------------------------------------------------------------
// Surface wind
// ---------------------------------------------------------------------------

/** Below this the wind does not favour a runway — 3 kt, where a sock stops indicating. */
export const MIN_DECISIVE_WIND_KT = 3
export const SOCK_LIMP_KT = 3
/** A real sock is fully extended at about 15 kt — that is what a 15-knot sock means. */
export const SOCK_FULL_KT = 15
const CROSSWIND_TIE_WEIGHT = 0.001

export interface WindComponents {
  headwindKt: number
  crosswindKt: number
  /** A positive offset means the wind comes from the right, seen from the cockpit. */
  fromRight: boolean
}

function signedOffsetDeg(deltaDeg: number): number {
  let offset = deltaDeg % 360
  if (offset > 180) offset -= 360
  if (offset < -180) offset += 360
  return offset
}

export function windComponents(
  runwayHeadingDeg: number,
  windFromDeg: number,
  windSpeedKt: number,
): WindComponents {
  const offsetDeg = signedOffsetDeg(windFromDeg - runwayHeadingDeg)
  const radians = toRadians(offsetDeg)
  return {
    headwindKt: windSpeedKt * Math.cos(radians),
    crosswindKt: Math.abs(windSpeedKt * Math.sin(radians)),
    fromRight: offsetDeg > 0,
  }
}

/**
 * The index of the end a pilot would most likely use, or `null` when there is
 * nothing to choose between.
 *
 * Most headwind wins, and a tie on headwind goes to the least crosswind. That
 * ordering is the right way round: a tailwind lengthens the ground roll on every
 * takeoff and landing, while a crosswind within limits is a technique problem.
 * Below 3 kt it returns `null` rather than inventing a recommendation.
 */
export function favouredEnd(
  runwayHeadingsDeg: number[],
  windFromDeg: number | null | undefined,
  windSpeedKt: number | null | undefined,
): number | null {
  if (runwayHeadingsDeg.length === 0) return null
  if (windFromDeg == null) return null
  if (windSpeedKt == null || windSpeedKt < MIN_DECISIVE_WIND_KT) return null

  let best = -1
  let bestKey = Number.MAX_VALUE
  for (let i = 0; i < runwayHeadingsDeg.length; i++) {
    const c = windComponents(runwayHeadingsDeg[i], windFromDeg, windSpeedKt)
    // Crosswind is scaled far below headwind's range so it can only break a tie,
    // never outvote it.
    const key = -c.headwindKt + c.crosswindKt * CROSSWIND_TIE_WEIGHT
    if (key < bestKey) {
      bestKey = key
      best = i
    }
  }
  return best >= 0 ? best : null
}

/**
 * How far a windsock stands off its mast, from 0 (hanging limp) to 1 (streaming).
 *
 * **Lift is length, not droop.** This is a plan view, and from directly above you
 * cannot see a sock hang — you see it foreshortened.
 */
export function sockLift(windSpeedKt: number | null | undefined): number {
  const speed = windSpeedKt ?? 0
  if (speed <= SOCK_LIMP_KT) return 0
  if (speed >= SOCK_FULL_KT) return 1
  return (speed - SOCK_LIMP_KT) / (SOCK_FULL_KT - SOCK_LIMP_KT)
}
