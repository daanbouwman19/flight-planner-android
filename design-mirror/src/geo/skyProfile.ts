/**
 * Sky-profile geometry, ported from `SkyProfileGeometry.kt`.
 *
 * The profile is **not a picture of the sky**. It is a vertical cross-section read
 * the way an approach plate's profile view is: altitude up the Y axis, every deck
 * at its true base, the flight-rules thresholds as hairlines, the ground at the
 * bottom. Its whole value is that the flight category becomes a *consequence of
 * the geometry* — there is no arrangement of this drawing that shows a 700 ft
 * overcast as a nice day, which is the defect the original cartoon sky had.
 */

export type CloudCover = 'FEW' | 'SCATTERED' | 'BROKEN' | 'OVERCAST'
export type FlightRulesKey = 'VFR' | 'MVFR' | 'IFR' | 'LIFR'

/** Ordinals, matching the Kotlin enum's declaration order — the PRNG seeds use them. */
const COVER_ORDINAL: Record<CloudCover, number> = {
  FEW: 0,
  SCATTERED: 1,
  BROKEN: 2,
  OVERCAST: 3,
}

const NOMINAL_OCTAS: Record<CloudCover, number> = { FEW: 1, SCATTERED: 3, BROKEN: 6, OVERCAST: 8 }

/**
 * The axis, as (feet, fraction) breakpoints.
 *
 * **Deliberately not linear.** It spends 56 % of its height on the first 3,000 ft,
 * with breakpoints *at* the flight-rules thresholds. Linear, a 900 ft ceiling and
 * a 2,900 ft one — the difference between IFR and MVFR — would be two hairlines a
 * few pixels apart. The cost is a compressed upper air, which is the right trade:
 * nobody reads a cirrus base to two significant figures and everybody reads a
 * ceiling.
 */
export const AXIS_BREAKPOINTS: Array<[number, number]> = [
  [0, 0.0],
  [500, 0.2],
  [1000, 0.36],
  [3000, 0.56],
  [12000, 0.78],
  [45000, 1.0],
]

/** The top of the axis. Anything above this is drawn at the top, not off it. */
export const AXIS_TOP_FT = 45000

/**
 * The three ceiling thresholds, each with the category that begins *below* it.
 *
 * Drawn as hairlines in the flight-rules colours — **the only place those colours
 * appear in the scene**, because they belong to the measuring apparatus rather
 * than to the weather. Nothing in a sky, cloud or ground fill is ever a
 * flight-rules colour.
 */
export const CEILING_THRESHOLDS: Array<[number, FlightRulesKey]> = [
  [500, 'LIFR'],
  [1000, 'IFR'],
  [3000, 'MVFR'],
]

/**
 * Where `ft` sits on the axis, from 0 (ground) to 1 (top).
 *
 * Monotonic and continuous. Negative altitudes clamp to 0 — a below-sea-level
 * field still reports a cloud base in feet above ground, so a negative value here
 * would be a data error, and drawing it below the ground would make the scene lie
 * about which side of the surface the cloud is on.
 */
export function altitudeToFraction(ft: number): number {
  if (ft <= 0) return 0
  if (ft >= AXIS_TOP_FT) return 1
  for (let i = 0; i < AXIS_BREAKPOINTS.length - 1; i++) {
    const [lowFt, lowFraction] = AXIS_BREAKPOINTS[i]
    const [highFt, highFraction] = AXIS_BREAKPOINTS[i + 1]
    if (ft <= highFt) {
      const t = (ft - lowFt) / (highFt - lowFt)
      return lowFraction + t * (highFraction - lowFraction)
    }
  }
  return 1
}

export interface CloudLayer {
  cover: CloudCover
  baseFt: number
  /** `CB` or `TCU`, when the station reported one. */
  convective?: 'CB' | 'TCU' | null
}

export interface CloudDeck extends CloudLayer {
  /** 1 for a deck that is exactly what the station reported; more when merged. */
  mergedCount: number
}

/** Decks closer than this on the axis are merged rather than drawn overlapping. */
export const MIN_DECK_SEPARATION = 0.06
export const MAX_DECK_FRACTION = 0.94
export const MAX_INFERRED_FOG_FRACTION = 0.14
export const MAX_THICKNESS_OF_GAP = 0.65

/**
 * Collapses reported layers that would overlap on the axis into single decks.
 *
 * The compressed upper air means two layers a thousand feet apart at 30,000 ft are
 * a few pixels apart on the ruler, and drawn separately they read as one smeared
 * band rather than as two decks.
 */
const isCeiling = (cover: CloudCover) => cover === 'BROKEN' || cover === 'OVERCAST'

export function mergeDecks(layers: CloudLayer[]): CloudDeck[] {
  if (layers.length === 0) return []
  const sorted = [...layers].sort((a, b) => a.baseFt - b.baseFt)
  const groups: CloudLayer[][] = []
  for (const layer of sorted) {
    const group = groups[groups.length - 1]
    const previous = group?.[group.length - 1]
    // A ceiling and a non-ceiling never merge however close they are: collapsing
    // a FEW into a BKN would invent a ceiling the station never reported, and the
    // whole scene's flight category follows from where the ceilings are.
    const mergeable =
      previous != null &&
      isCeiling(previous.cover) === isCeiling(layer.cover) &&
      altitudeToFraction(layer.baseFt) - altitudeToFraction(previous.baseFt) < MIN_DECK_SEPARATION
    if (mergeable) group.push(layer)
    else groups.push([layer])
  }
  return groups.map((group) => ({
    // The heaviest cover in the group, and the *lowest* base: a merged deck must
    // never claim the sky is more open, or higher, than it was reported.
    cover: group.reduce((a, b) => (NOMINAL_OCTAS[a.cover] >= NOMINAL_OCTAS[b.cover] ? a : b)).cover,
    baseFt: group[0].baseFt,
    convective: group.find((l) => l.convective != null)?.convective ?? null,
    mergedCount: group.length,
  }))
}

/**
 * Each deck's base as an axis fraction, separated so two never sit on top of
 * each other.
 *
 * Pushing upward can run the top deck off the axis, so the whole stack is pulled
 * back down by the overflow rather than clipped — clipping would silently drop a
 * reported layer.
 */
export function deckFractions(decks: CloudDeck[]): number[] {
  if (decks.length === 0) return []
  const fractions = decks.map((d) => altitudeToFraction(d.baseFt))
  for (let i = 1; i < fractions.length; i++) {
    const minimum = fractions[i - 1] + MIN_DECK_SEPARATION
    if (fractions[i] < minimum) fractions[i] = minimum
  }
  const overflow = fractions[fractions.length - 1] - MAX_DECK_FRACTION
  if (overflow > 0) {
    const shift = Math.min(overflow, fractions[0])
    for (let i = 0; i < fractions.length; i++) fractions[i] -= shift
  }
  return fractions
}

export function deckThicknessFraction(cover: CloudCover): number {
  return { FEW: 0.055, SCATTERED: 0.065, BROKEN: 0.078, OVERCAST: 0.09 }[cover]
}

/** Opacity rises with the reported octas, so a deck's weight is legible as weight. */
export function deckOpacity(cover: CloudCover): number {
  return 0.45 + 0.55 * (NOMINAL_OCTAS[cover] / NOMINAL_OCTAS.OVERCAST)
}

export function deckThicknesses(decks: CloudDeck[], fractions: number[]): number[] {
  return decks.map((deck, i) => {
    const nominal = deckThicknessFraction(deck.cover)
    if (i + 1 >= fractions.length) return nominal
    const room = (fractions[i + 1] - fractions[i]) * MAX_THICKNESS_OF_GAP
    return Math.min(nominal, room)
  })
}

export function deckLobes(cover: CloudCover): number {
  return { FEW: 2, SCATTERED: 3, BROKEN: 4, OVERCAST: 6 }[cover]
}

export function deckLobeAmplitude(cover: CloudCover): number {
  return { FEW: 1.0, SCATTERED: 0.92, BROKEN: 0.86, OVERCAST: 0.8 }[cover]
}

export function deckShoulder(cover: CloudCover): number {
  return { FEW: 0.12, SCATTERED: 0.32, BROKEN: 0.58, OVERCAST: 0.8 }[cover]
}

export function baseInsetFor(cover: CloudCover): number {
  return { FEW: 0.22, SCATTERED: 0.12, BROKEN: 0.04, OVERCAST: 0 }[cover]
}

// ---------------------------------------------------------------------------
// The seeded jitter
//
// The Kotlin uses a 64-bit LCG so a given report always draws the same picture —
// a deck that reshuffled on every recomposition would read as weather changing.
// Reproduced here with BigInt rather than approximated, so the mirror and the app
// place their lobes identically for the same report.
// ---------------------------------------------------------------------------

const MASK64 = (1n << 64n) - 1n
const LCG_MULTIPLIER = 6364136223846793005n
const LCG_INCREMENT = 1442695040888963407n

function nextUnit(seed: bigint): [bigint, number] {
  const next = (seed * LCG_MULTIPLIER + LCG_INCREMENT) & MASK64
  const bits = next >> 33n
  return [next, Math.min(Math.max(Number(bits) / 2 ** 31, 0), 1)]
}

export interface DeckSpan {
  start: number
  end: number
}

/**
 * The horizontal runs one deck is drawn as.
 *
 * An overcast is a single lid across the whole frame; anything less is broken into
 * runs whose total width is the reported coverage. **Stratified**: one run per
 * equal cell, so the deck spreads across the frame instead of clustering wherever
 * the sequence happens to start.
 */
export function deckSpans(deck: CloudDeck): DeckSpan[] {
  if (deck.cover === 'OVERCAST') return [{ start: 0, end: 1 }]

  const coverage = { FEW: 0.22, SCATTERED: 0.46, BROKEN: 0.74, OVERCAST: 1.0 }[deck.cover]
  const runs = deck.cover === 'FEW' ? 2 : 3
  const cell = 1 / runs
  const runWidth = coverage / runs

  let seed =
    (BigInt(deck.baseFt) * 2654435761n + BigInt(COVER_ORDINAL[deck.cover]) * 40503n) & MASK64
  const spans: DeckSpan[] = []
  for (let index = 0; index < runs; index++) {
    const [next, jitter] = nextUnit(seed)
    seed = next
    const slack = cell - runWidth
    const start = index * cell + jitter * slack
    spans.push({ start, end: start + runWidth })
  }
  return spans
}

/**
 * The relative heights of one run's lobes.
 *
 * Never below 60 % of the tallest: a lobe much shorter than its neighbours reads
 * as a gap in the deck rather than as its texture.
 */
export function lobeHeights(cover: CloudCover, baseFt: number, runIndex: number): number[] {
  const lobes = deckLobes(cover)
  const amplitude = deckLobeAmplitude(cover)
  let seed =
    (BigInt(baseFt) * 2654435761n +
      BigInt(runIndex) * 97003n +
      BigInt(COVER_ORDINAL[cover]) * 7919n) &
    MASK64
  const heights: number[] = []
  for (let i = 0; i < lobes; i++) {
    const [next, unit] = nextUnit(seed)
    seed = next
    heights.push(amplitude * (0.6 + 0.4 * unit))
  }
  return heights
}

export type SkyCover =
  | { kind: 'unknown' }
  | { kind: 'clear' }
  | { kind: 'layers'; layers: CloudLayer[] }
  /** The sky is not visible at all; `verticalVisibilityFt` is how far up you can see. */
  | { kind: 'obscured'; verticalVisibilityFt?: number | null }

/**
 * How deep the fog slab is, as a fraction of the axis.
 *
 * **Fog is an opaque slab with a hard top edge, not a fade.** Drawn as a gradient
 * into the air it met the sky at about 1.1:1, so the headline case — a half-mile
 * `FG VV002` field — looked like a clear day with a wash at the bottom. Fog is not
 * a tint on the air; it is a surface, with a top you can see from above and cannot
 * see through.
 */
export function fogHeightFraction(
  skyCover: SkyCover,
  fogOrMistReported: boolean,
  visibilityStatuteMiles?: number | null,
): number {
  if (skyCover.kind === 'obscured') {
    const depth = skyCover.verticalVisibilityFt
    if (depth == null) return MAX_INFERRED_FOG_FRACTION
    return altitudeToFraction(depth)
  }
  if (skyCover.kind === 'unknown') return 0
  if (!fogOrMistReported) return 0
  if (visibilityStatuteMiles == null) return MAX_INFERRED_FOG_FRACTION * 0.5
  // Thicker fog at lower visibility, flattening above 3 SM where "mist" stops
  // meaning much about depth.
  const thickness = Math.min(Math.max((3 - visibilityStatuteMiles) / 3, 0), 1)
  return MAX_INFERRED_FOG_FRACTION * thickness
}

export type SkyPhase = 'DAY' | 'TWILIGHT' | 'NIGHT'

/** Where a body sits across the frame: east right, west left, north and south folded to centre. */
export const RAIL_HORIZON_FRACTION = 0.86
export const RAIL_ZENITH_FRACTION = 0.935

/**
 * `sin(azimuth)` places a body across the frame.
 *
 * Once the section is cut east–west, that fold *is* the projection: north and
 * south both land in the centre because neither has an east–west component.
 */
export function railX(azimuthDeg: number): number {
  return 0.5 + 0.5 * Math.sin((azimuthDeg * Math.PI) / 180)
}

/**
 * Where a body sits on the horizon rail.
 *
 * **The rail is not the altitude axis, and must never invite a reader to drop a
 * horizontal onto the ruler** — a body has an elevation *angle*, not an altitude
 * in feet. The defence is that the highest ceiling threshold, 3,000 ft, sits at
 * fraction 0.56, so no hairline, tick or numeral exists anywhere near the rail.
 */
export function railY(elevationDeg: number): number {
  const fraction = Math.min(Math.max(elevationDeg / 90, 0), 1)
  return RAIL_HORIZON_FRACTION + fraction * (RAIL_ZENITH_FRACTION - RAIL_HORIZON_FRACTION)
}

/**
 * How much of the sun or moon shows through the decks.
 *
 * **Occlusion is alpha, not painting order.** The rail sits above almost every
 * deck, so paint order would let a cirrus hide the sun while a 700 ft overcast let
 * it shine through — the drawing lying, in the exact shape this redesign removes.
 * Keyed to the lowest ceiling instead.
 */
export function celestialAlpha(skyCover: SkyCover): number {
  if (skyCover.kind === 'unknown') return 0
  if (skyCover.kind === 'clear') return 1
  if (skyCover.kind === 'obscured') return 0.1
  // The *lowest* ceiling decides — a ceiling being broken or worse, which is the
  // standard definition and the one the flight-rules thresholds are written
  // against. Few and scattered are cloud you can see but not cloud that stops you.
  const ceilings = skyCover.layers.filter((l) => l.cover === 'BROKEN' || l.cover === 'OVERCAST')
  if (ceilings.length === 0) return 0.85
  const lowest = ceilings.reduce((a, b) => (a.baseFt <= b.baseFt ? a : b))
  return lowest.cover === 'BROKEN' ? 0.35 : 0.18
}

export interface SkyBlend {
  from: SkyPhase
  to: SkyPhase
  weight: number
}

/**
 * Which two bands the air is mixed from, at a given solar elevation.
 *
 * **Blending is not always safe**, which is why a caller checks polarity first:
 * where the ink reverses between two bands, the intermediate value theorem
 * guarantees a blend point at which ink and air have equal luminance and a deck's
 * underside becomes a 1.0:1 line. No third ink exists — the endpoint constraints
 * contradict — so that one crossing is driven as a fast traversal instead of by
 * the sun's elevation, turning twenty minutes of an invisible edge into a few
 * hundred milliseconds of one.
 */
export const DAY_ELEVATION_DEG = 6.0
export const TWILIGHT_ELEVATION_DEG = -4.0
export const NIGHT_ELEVATION_DEG = -12.0

export function skyBlendFor(elevationDeg: number): SkyBlend {
  if (elevationDeg >= DAY_ELEVATION_DEG) return { from: 'DAY', to: 'TWILIGHT', weight: 0 }
  if (elevationDeg >= TWILIGHT_ELEVATION_DEG) {
    return {
      from: 'DAY',
      to: 'TWILIGHT',
      weight: (DAY_ELEVATION_DEG - elevationDeg) / (DAY_ELEVATION_DEG - TWILIGHT_ELEVATION_DEG),
    }
  }
  if (elevationDeg >= NIGHT_ELEVATION_DEG) {
    return {
      from: 'TWILIGHT',
      to: 'NIGHT',
      weight:
        (TWILIGHT_ELEVATION_DEG - elevationDeg) / (TWILIGHT_ELEVATION_DEG - NIGHT_ELEVATION_DEG),
    }
  }
  return { from: 'TWILIGHT', to: 'NIGHT', weight: 1 }
}
