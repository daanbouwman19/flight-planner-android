import { FlightPlannerTheme, ImmersiveGlobeScreen } from '@flightplanner/design-mirror'

const EHAM = { icao: 'EHAM', lat: 52.308601, lon: 4.76389, rules: 'VFR' as const }
const KJFK = { icao: 'KJFK', lat: 40.639447, lon: -73.779317, rules: 'IFR' as const }
const RJTT = { icao: 'RJTT', lat: 35.552258, lon: 139.779694 }
const KLAX = { icao: 'KLAX', lat: 33.942536, lon: -118.408075 }

/**
 * The globe with the window to itself — the screen the route detail's fullscreen
 * action pushes into. The camera keeps its state across the transition in the
 * app, so the frame grows while the globe holds still.
 *
 * ### Why the figures come back here
 *
 * The route detail's spine says everything a chip would, in the right place,
 * which is why the deep hero carries none. Here there is no spine and no page:
 * without one line of figures the screen is a picture of a planet with no idea
 * which flight it is about. So the route reduces to one plate — the pair, the
 * airframe, and the three figures the arc is about.
 */
export const TransAtlantic = () => (
  <ImmersiveGlobeScreen
    departure={EHAM}
    destination={KJFK}
    aircraft="B777-300ER"
    distance="3,153 NM"
    bearing="291°"
    flightTime="7:04"
  />
)

/** A Pacific long-haul, the arc bending over the pole. */
export const LongHaul = () => (
  <ImmersiveGlobeScreen
    departure={RJTT}
    destination={KLAX}
    aircraft="A350-1000"
    distance="4,748 NM"
    bearing="055°"
    flightTime="9:41"
  />
)

/** In Cockpit, for flying at night. */
export const Cockpit = () => (
  <FlightPlannerTheme theme="cockpit">
    <ImmersiveGlobeScreen
      departure={EHAM}
      destination={KJFK}
      aircraft="B777-300ER"
      distance="3,153 NM"
      bearing="291°"
      flightTime="7:04"
    />
  </FlightPlannerTheme>
)
