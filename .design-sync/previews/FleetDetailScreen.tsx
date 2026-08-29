import { FleetDetailScreen, FlightPlannerTheme } from '@flightplanner/design-mirror'

const c172 = {
  variant: 'Cessna 172S Skyhawk',
  category: 'Single Engine Piston',
  range: '640 NM',
  requiredRunway: '1,685 ft',
  flights: 18,
}

/**
 * One airframe, as its own screen.
 *
 * The phone form of what a tablet shows as `FleetDetailPane` beside the list. It
 * is the same component inside a frame rather than a second layout, so the two
 * cannot drift apart — and the name lives in the app bar rather than being stated
 * twice, which is what `showTitle` is for.
 */
export const Default = () => <FleetDetailScreen aircraft={c172} cruiseSpeed="124 kt" />

/** An airframe with nothing logged against it yet. */
export const NeverFlown = () => (
  <FleetDetailScreen
    aircraft={{
      variant: 'Bombardier Global 7500',
      category: 'Business Jet',
      range: '7,700 NM',
      requiredRunway: '5,760 ft',
    }}
    cruiseSpeed="488 kt"
  />
)

/** In Cockpit — the dark scheme a pilot reads at night. */
export const Cockpit = () => (
  <FlightPlannerTheme theme="cockpit">
    <FleetDetailScreen aircraft={c172} cruiseSpeed="124 kt" />
  </FlightPlannerTheme>
)
