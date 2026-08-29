import { FlightPlannerTheme, HeroDistanceCard } from '@flightplanner/design-mirror'

/**
 * The headline of the statistics screen.
 *
 * The pill under the figure is the point of the component. `48,213 NM` is a
 * number a reader cannot hold; "2.2 × around the Earth" is one they can, and it
 * costs a single line.
 */
export const Default = () => (
  <div style={{ width: 360 }}>
    <HeroDistanceCard totalDistance="48,213 NM" earthCircumferences="2.2 × around the Earth" />
  </div>
)

/**
 * A short logbook.
 *
 * The comparison still has to say something true at this scale, which is why it
 * is a caller-formatted string rather than a ratio this component computes: at
 * 140 NM, "0.006 × around the Earth" would be arithmetic rather than meaning.
 */
export const EarlyDays = () => (
  <div style={{ width: 360 }}>
    <HeroDistanceCard totalDistance="140 NM" earthCircumferences="Amsterdam to Paris and back" />
  </div>
)

/** In Chart, where the figure is set on a printed page rather than a lit screen. */
export const Chart = () => (
  <FlightPlannerTheme theme="chart">
    <div style={{ width: 360 }}>
      <HeroDistanceCard totalDistance="48,213 NM" earthCircumferences="2.2 × around the Earth" />
    </div>
  </FlightPlannerTheme>
)
