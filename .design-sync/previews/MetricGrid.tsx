import { MetricGrid } from '@flightplanner/design-mirror'

/** The four secondary figures under the hero — two rows of two on a phone. */
export const Default = () => (
  <div style={{ width: 360 }}>
    <MetricGrid
      metrics={[
        { label: 'FLIGHTS', value: '42' },
        { label: 'HOURS', value: '63:25' },
        { label: 'AIRPORTS', value: '31' },
        { label: 'LONGEST', value: '3,153 NM' },
      ]}
    />
  </div>
)

/**
 * Widths that would drift under a centred setting.
 *
 * Every value here is set in tabular figures and aligned the same way, so `7`
 * and `12,467 ft` sit on the same edge. A column of figures is read down its
 * edges; one that re-centres per row makes the eye re-find the number.
 */
export const UnevenWidths = () => (
  <div style={{ width: 360 }}>
    <MetricGrid
      metrics={[
        { label: 'FLIGHTS', value: '7' },
        { label: 'HOURS', value: '1,204:55' },
        { label: 'AIRPORTS', value: '3' },
        { label: 'LONGEST', value: '12,467 NM' },
      ]}
    />
  </div>
)
