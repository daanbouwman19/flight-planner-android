import { FilterField } from '@flightplanner/design-mirror'

const pair = { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, width: 328, alignItems: 'stretch' as const }

/**
 * A set pair, each carrying its detail line.
 *
 * The detail is the point: a chip can say *what* is set, a field can say what that
 * **means** — "640 NM · 1,685 ft" is the envelope every route was generated inside.
 */
export const BothSet = () => (
  <div style={pair}>
    <FilterField label="DEPARTURE" value="EHAM" detail="Amsterdam Airport Schiphol" selected />
    <FilterField label="AIRCRAFT" value="C172S" detail="640 NM · 1,685 ft" selected />
  </div>
)

/** Neither set: outlined and receding, because an unset filter is the absence of a constraint. */
export const Unset = () => (
  <div style={pair}>
    <FilterField label="DEPARTURE" value="Any" selected={false} />
    <FilterField label="AIRCRAFT" value="Any" selected={false} />
  </div>
)

/** One of each — the state the screen is usually in, and where the contrast earns its keep. */
export const Mixed = () => (
  <div style={pair}>
    <FilterField label="DEPARTURE" value="Any" selected={false} />
    <FilterField label="AIRCRAFT" value="PC-12" detail="1,803 NM · 2,438 ft" selected />
  </div>
)
