import { LogbookScreen } from '@flightplanner/design-mirror'

const months = [
  {
    label: 'August 2026',
    flights: [
      { departure: 'EHAM', destination: 'EBBR', aircraft: 'C172S', date: '24 Aug', distance: '92 NM', duration: '0:52' },
      { departure: 'EHRD', destination: 'EHAM', aircraft: 'DA40', date: '19 Aug', distance: '31 NM', duration: '0:19' },
      { departure: 'EGLL', destination: 'LFPG', aircraft: 'CJ4', date: '11 Aug', distance: '188 NM', duration: '0:41' },
    ],
  },
  {
    label: 'July 2026',
    flights: [
      { departure: 'LSZH', destination: 'LIRF', aircraft: 'PC-12', date: '28 Jul', distance: '424 NM', duration: '2:11' },
      { departure: 'ESSA', destination: 'EFHK', aircraft: 'SR22T', date: '14 Jul', distance: '209 NM', duration: '1:44' },
    ],
  },
]

/** Two months of flights with the running totals above them. */
export const Default = () => (
  <LogbookScreen
    months={months}
    summary={[
      { label: 'FLIGHTS', value: '42' },
      { label: 'NM', value: '48,213' },
      { label: 'HOURS', value: '63:25' },
    ]}
  />
)

/** A single month, no summary — the shape a new logbook has. */
export const OneMonth = () => <LogbookScreen months={months.slice(0, 1)} />
