import { StatSummaryStrip } from '@flightplanner/design-mirror'

/** The logbook's running totals — a few large centred figures under their captions. */
export const LogbookTotals = () => (
  <div style={{ width: 328 }}>
    <StatSummaryStrip
      tiles={[
        { label: 'FLIGHTS', value: '42' },
        { label: 'NM', value: '48,213' },
        { label: 'HOURS', value: '63:25' },
      ]}
    />
  </div>
)

/** Two tiles rather than three, for a narrower context. */
export const TwoTiles = () => (
  <div style={{ width: 240 }}>
    <StatSummaryStrip
      tiles={[
        { label: 'AIRPORTS', value: '31' },
        { label: 'AIRCRAFT', value: '7' },
      ]}
    />
  </div>
)
