import { EditEnvelopeSheet, PhoneFrame, ScrimOverlay } from '@flightplanner/design-mirror'

/**
 * Revising one airframe's envelope.
 *
 * Deliberately the three figures alone rather than the whole add form: these are
 * the numbers a user actually revises. A published range is a book value; the one
 * they fly to is theirs.
 */
export const Default = () => (
  <PhoneFrame>
    <ScrimOverlay>
      <EditEnvelopeSheet aircraft="Cessna 172S Skyhawk" range="640" cruise="124" takeoff="1,685" />
    </ScrimOverlay>
  </PhoneFrame>
)

/** A larger airframe, where the figures run wider. */
export const Wide = () => (
  <PhoneFrame>
    <ScrimOverlay>
      <EditEnvelopeSheet aircraft="Bombardier Global 7500" range="7,700" cruise="488" takeoff="5,760" />
    </ScrimOverlay>
  </PhoneFrame>
)
