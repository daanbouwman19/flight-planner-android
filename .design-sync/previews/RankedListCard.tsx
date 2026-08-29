import { RankedListCard } from '@flightplanner/design-mirror'

/** Most-flown airframes: a name and a count, with no code column. */
export const MostFlown = () => (
  <div style={{ width: 360 }}>
    <RankedListCard
      title="Most flown"
      rows={[
        { name: 'Cessna 172S Skyhawk', count: 18 },
        { name: 'Diamond DA40 NG', count: 11 },
        { name: 'Cirrus SR22T', count: 7 },
      ]}
    />
  </div>
)

/**
 * Most-visited airports — the same component, with the ICAO leading each row.
 *
 * One component for both lists, because they are the same object: a name, an
 * optional code, and a count aligned down the trailing edge in tabular figures.
 * Two components that merely looked alike would come apart the first time one of
 * them was retuned.
 */
export const MostVisited = () => (
  <div style={{ width: 360 }}>
    <RankedListCard
      title="Most visited"
      rows={[
        { code: 'EHAM', name: 'Schiphol', count: 14 },
        { code: 'EGLL', name: 'Heathrow', count: 9 },
        { code: 'EDDF', name: 'Frankfurt', count: 6 },
      ]}
    />
  </div>
)
