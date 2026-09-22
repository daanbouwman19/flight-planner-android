import { WatchFrame, WatchRouteMap } from '@flightplanner/design-mirror'

/**
 * The map alone, on the round glass: solid land, a quiet coast, the route
 * cased in the watch's ground. The top fifth is kept clear for the codes.
 */
export const LongHaul = () => (
  <WatchFrame>
    <WatchRouteMap depLat={35.3398} depLon={-99.2005} destLat={38.4353} destLon={38.091} />
  </WatchFrame>
)

/** A diagonal leg: framed for the circle, so both ends stay on the glass rather than in the square's corners. */
export const Diagonal = () => (
  <WatchFrame>
    <WatchRouteMap depLat={52.3086} depLon={4.7639} destLat={40.6394} destLon={-73.7793} />
  </WatchFrame>
)
