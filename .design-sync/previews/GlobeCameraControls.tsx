import { GlobeCameraControls } from '@flightplanner/design-mirror'

/**
 * A backdrop the translucent glass can read against — the theme's own day sky at
 * its high end, the colour the globe's own backdrop is.
 */
const onGlass = (node: React.ReactNode) => (
  <div
    style={{
      display: 'inline-flex',
      padding: 20,
      borderRadius: 12,
      background: 'var(--fp-sky-day-high)',
    }}
  >
    {node}
  </div>
)

/**
 * The camera controls, as one instrument rather than four buttons — zoom in,
 * zoom out, and a "frame the route" crop mark, on the translucent plate every
 * mark this app makes over the globe's imagery is made on.
 *
 * The zoom marks are **type, not icons**: a plus and a minus at the app's
 * hairline weight, because there is nothing in a stock `zoom_in` vector a cross
 * does not say. The plate is inert in the mirror — the globe here does not move —
 * the way `BottomSheet`'s drag handle is drawn and inert.
 */
export const AtRest = () => onGlass(<GlobeCameraControls />)

/**
 * When the view has been turned, the plate grows a compass cell: a two-tone
 * needle over the bearing set as a figure — `047°`, in the tabular face this app
 * states every other angle in. It is a readout and a "face north" control at
 * once, and it appears rather than greys out because facing north when you
 * already face north is a control that does nothing.
 */
export const Turned = () => onGlass(<GlobeCameraControls bearingDegrees={47} />)
