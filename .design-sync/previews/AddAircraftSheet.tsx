import { AddAircraftSheet, PhoneFrame, ScrimOverlay } from '@flightplanner/design-mirror'

/**
 * Adding an airframe.
 *
 * The envelope fields carry their units as suffixes rather than in the label: a
 * reader entering 640 needs to know it is nautical miles at the moment they type
 * it, not from a caption somewhere above.
 */
export const Filled = () => (
  <PhoneFrame>
    <ScrimOverlay>
      <AddAircraftSheet
        manufacturer="Cessna"
        variant="172S Skyhawk"
        icaoCode="C172"
        category="Single Engine Piston"
        range="640"
        cruise="124"
        takeoff="1,685"
      />
    </ScrimOverlay>
  </PhoneFrame>
)

/** An empty form, as it opens. */
export const Empty = () => (
  <PhoneFrame>
    <ScrimOverlay>
      <AddAircraftSheet />
    </ScrimOverlay>
  </PhoneFrame>
)

/**
 * Submitted and rejected.
 *
 * Every failing field states its own reason on itself. A single banner at the top
 * saying "check your entries" makes the user hunt for which one.
 */
export const Invalid = () => (
  <PhoneFrame>
    <ScrimOverlay>
      <AddAircraftSheet
        manufacturer="Cessna"
        variant=""
        range="0"
        cruise="124"
        takeoff="1,685"
        errors={{ variant: 'Required', range: 'Must be above zero' }}
      />
    </ScrimOverlay>
  </PhoneFrame>
)
