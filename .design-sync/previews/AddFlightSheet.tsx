import { AddFlightSheet, PhoneFrame, ScrimOverlay } from '@flightplanner/design-mirror'

/**
 * Logging a flight that has been flown.
 *
 * The two ends are ICAO codes rather than a picker over every airport: a flight
 * being logged has already happened, and the user knows what they flew.
 */
export const Filled = () => (
  <PhoneFrame>
    <ScrimOverlay>
      <AddFlightSheet
        departure="EHAM"
        destination="EGLL"
        aircraft="Cessna 172S Skyhawk"
        date="29 Aug 2026"
        duration="1:12"
      />
    </ScrimOverlay>
  </PhoneFrame>
)

/** Empty, as it opens from the logbook's action. */
export const Empty = () => (
  <PhoneFrame>
    <ScrimOverlay>
      <AddFlightSheet date="29 Aug 2026" />
    </ScrimOverlay>
  </PhoneFrame>
)

/** Submitted with an end that is not an ICAO code and a duration that is not a time. */
export const Invalid = () => (
  <PhoneFrame>
    <ScrimOverlay>
      <AddFlightSheet
        departure="EHAM"
        destination="Heathrow"
        aircraft="Cessna 172S Skyhawk"
        date="29 Aug 2026"
        duration="an hour"
        errors={{ destination: 'Four-letter ICAO code', duration: 'Use h:mm' }}
      />
    </ScrimOverlay>
  </PhoneFrame>
)
