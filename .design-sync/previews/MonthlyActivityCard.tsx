import { MonthlyActivityCard } from '@flightplanner/design-mirror'

/**
 * Flights per month.
 *
 * Bars rather than a line: the quantity is a count of discrete events per
 * bucket, and a line drawn between two months claims a continuity that flying
 * does not have.
 */
export const Default = () => (
  <div style={{ width: 360 }}>
    <MonthlyActivityCard
      months={[
        { label: 'M', value: 2 },
        { label: 'J', value: 5 },
        { label: 'J', value: 7 },
        { label: 'A', value: 9 },
        { label: 'S', value: 4 },
        { label: 'O', value: 6 },
      ]}
    />
  </div>
)

/**
 * A year, with months nobody flew.
 *
 * An empty month keeps its column and its label rather than being dropped. The
 * gap *is* the information — a chart that closed up over January would say the
 * year had eleven months in it.
 */
export const AYearWithGaps = () => (
  <div style={{ width: 360 }}>
    <MonthlyActivityCard
      title="Flights by month · 2026"
      months={[
        { label: 'J', value: 0 },
        { label: 'F', value: 0 },
        { label: 'M', value: 3 },
        { label: 'A', value: 6 },
        { label: 'M', value: 11 },
        { label: 'J', value: 8 },
        { label: 'J', value: 2 },
        { label: 'A', value: 0 },
        { label: 'S', value: 5 },
        { label: 'O', value: 7 },
        { label: 'N', value: 1 },
        { label: 'D', value: 0 },
      ]}
    />
  </div>
)
