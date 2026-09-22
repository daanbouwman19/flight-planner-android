import { FlightPlannerTheme, WatchFrame } from '@flightplanner/design-mirror'

/**
 * The empty round display, 226 dp across — the reference watch. Nothing is
 * drawn outside the circle: no clock, no page indicator, no system bars.
 */
export const Empty = () => <WatchFrame />

/** The ground on a dark look is true black, not the phone's near-black, so the bezel disappears. */
export const Dark = () => (
  <FlightPlannerTheme theme="brandDark">
    <WatchFrame />
  </FlightPlannerTheme>
)
