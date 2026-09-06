import { FlightPlannerTheme, GlobeHero } from '@flightplanner/design-mirror'

// Real coordinates, read out of the app's own airports.db. A plausible-looking
// field in the wrong place is a confidently wrong drawing, and the whole point
// of the globe is that the framing answers "how far, across what" on sight.
const EHAM = { icao: 'EHAM', lat: 52.308601, lon: 4.76389, rules: 'VFR' as const }
const KJFK = { icao: 'KJFK', lat: 40.639447, lon: -73.779317, rules: 'IFR' as const }
const EBBR = { icao: 'EBBR', lat: 50.901402, lon: 4.48444, rules: 'VFR' as const }
const RJTT = { icao: 'RJTT', lat: 35.552258, lon: 139.779694, rules: 'MVFR' as const }
const KLAX = { icao: 'KLAX', lat: 33.942536, lon: -118.408075, rules: 'VFR' as const }

const frame = (node: React.ReactNode) => (
  <div style={{ width: 360, height: 340 }}>{node}</div>
)

/**
 * A trans-Atlantic leg. The fit pulls back until both ends are inside the
 * viewport with a plate's margin, and the arc bends over the curve between them.
 * The chrome — the camera stack, the credit — sits on the translucent glass
 * plates every mark this app makes over imagery is made on.
 */
export const TransAtlantic = () => frame(<GlobeHero departure={EHAM} destination={KJFK} />)

/**
 * A Pacific long-haul. The great circle runs up toward the pole rather than
 * straight across, which is the reading a flat map cannot give — and the reason
 * the sphere is offered at all.
 */
export const LongHaul = () => frame(<GlobeHero departure={RJTT} destination={KLAX} />)

/**
 * A short European hop. The two ends are closer than a plate's width, so they
 * become **one** plate holding both codes, anchored to the midpoint of the leg —
 * nudging them apart would put a code where no airport is. `GlobeFit` also floors
 * how close the camera settles, so a 90 NM leg still reads as a sphere.
 */
export const ShortHop = () => frame(<GlobeHero departure={EHAM} destination={EBBR} />)

/**
 * Under the app bar. `topChromeFraction` is the strip the host's chrome covers;
 * a label whose dot rises into it fades rather than moving off its airport.
 */
export const UnderChrome = () =>
  frame(<GlobeHero departure={EHAM} destination={KJFK} topChromeFraction={0.22} />)

/** In Cockpit — the night instrument panel. The sphere is themed too. */
export const Cockpit = () => (
  <FlightPlannerTheme theme="cockpit">
    {frame(<GlobeHero departure={EHAM} destination={KJFK} />)}
  </FlightPlannerTheme>
)
