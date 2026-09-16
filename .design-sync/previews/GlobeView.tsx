import { GlobeView, GlobeCamera, frameRoute, framePoints } from '@flightplanner/design-mirror'

const box = (node: React.ReactNode) => (
  <div style={{ width: 320, height: 320 }}>{node}</div>
)

/**
 * The sphere itself, with nothing on it — the world's coastline projected
 * through `GlobeCamera` and clipped to the limb, over the theme's own
 * high-altitude day sky. This is the base `GlobeHero` and `GlobeNetwork` build
 * on; use it directly when a concept needs a bare globe to draw its own marks on.
 *
 * The camera is five numbers (centre lat/lon, altitude, and a bearing and tilt
 * that are always zero in the mirror). Here it is aimed straight at the Atlantic.
 */
export const Atlantic = () =>
  box(<GlobeView camera={new GlobeCamera(35, -40, 1.4)} aspect={1} />)

/** Framed on a leg, the way `GlobeHero` does it — `frameRoute` picks the camera. */
export const FramedOnALeg = () =>
  box(
    <GlobeView
      camera={frameRoute(52.31, 4.76, 1.36, 103.99, { width: 320, height: 320 })}
      aspect={1}
    />,
  )

/** Framed on a set of points — `framePoints`, which is what the visited network uses. */
export const FramedOnARegion = () =>
  box(
    <GlobeView
      camera={framePoints(
        [52.31, 51.47, 50.03, 41.8, 40.49],
        [4.76, -0.46, 8.56, 12.25, -3.57],
        { width: 320, height: 320 },
      )}
      aspect={1}
    />,
  )

/** Pulled back to the whole planet — a raw camera, so the fit clamp does not apply. */
export const WholePlanet = () =>
  box(<GlobeView camera={new GlobeCamera(20, 10, 3)} aspect={1} />)
