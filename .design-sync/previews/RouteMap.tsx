import { RouteMap } from '@flightplanner/design-mirror'

/** The canonical long-haul leg: Amsterdam to New York, crossing the Atlantic. */
export const TransAtlantic = () => (
  <div style={{ width: 360 }}>
    <RouteMap depLat={52.3105} depLon={4.7683} destLat={40.6413} destLon={-73.7781} />
  </div>
)

/**
 * A short European hop, where the minimum-span floor does the work.
 *
 * Without it, Rotterdam to Amsterdam frames 0.4° of the world and the card fills
 * with a piece of the Dutch coast magnified past recognition.
 */
export const ShortHop = () => (
  <div style={{ width: 360 }}>
    <RouteMap depLat={51.9569} depLon={4.4372} destLat={52.3105} destLon={4.7683} />
  </div>
)

/**
 * Tokyo to Los Angeles, which crosses the antimeridian.
 *
 * The longitudes run from 140° up past 180° to 242° rather than wrapping, so the
 * route is drawn the short way and the coastline is shifted into the window by a
 * whole turn instead.
 */
export const CrossesTheSeam = () => (
  <div style={{ width: 360 }}>
    <RouteMap depLat={35.5494} depLon={139.7798} destLat={33.9416} destLon={-118.4085} />
  </div>
)

/**
 * Denver to Wichita — inland, with no coastline in the window.
 *
 * This is the case the graticule exists for: without it the card is a flat wash
 * that reads as a failed load rather than as a place.
 */
export const InlandGraticule = () => (
  <div style={{ width: 360 }}>
    <RouteMap depLat={39.8561} depLon={-104.6737} destLat={37.6499} destLon={-97.4331} />
  </div>
)
