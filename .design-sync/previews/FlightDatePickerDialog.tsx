import { FlightDatePickerDialog, PhoneFrame, ScrimOverlay } from '@flightplanner/design-mirror'

/**
 * Picking the date a flight was flown.
 *
 * **Future days are unselectable, not merely rejected.** A flight being logged has
 * already been flown, so the dialog states the constraint where the choice is made
 * rather than after it — a date you cannot pick teaches the rule; an error message
 * after you pick it does not.
 */
export const CurrentMonth = () => (
  <PhoneFrame>
    <ScrimOverlay>
      <FlightDatePickerDialog year={2026} month={7} selectedDay={24} maxDay={29} />
    </ScrimOverlay>
  </PhoneFrame>
)

/** A past month, where every day is selectable. */
export const PastMonth = () => (
  <PhoneFrame>
    <ScrimOverlay>
      <FlightDatePickerDialog year={2026} month={4} selectedDay={11} />
    </ScrimOverlay>
  </PhoneFrame>
)

/**
 * Opened with nothing chosen.
 *
 * The headline says so in words rather than showing today's date greyed out —
 * a pre-filled headline the user did not choose is a value they will submit by
 * accident.
 */
export const NoSelection = () => (
  <PhoneFrame>
    <ScrimOverlay>
      <FlightDatePickerDialog year={2026} month={7} maxDay={29} />
    </ScrimOverlay>
  </PhoneFrame>
)
