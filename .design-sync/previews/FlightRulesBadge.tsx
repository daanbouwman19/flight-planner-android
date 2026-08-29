import { FlightPlannerTheme, FlightRulesBadge } from '@flightplanner/design-mirror'

const row = { display: 'flex', gap: 8, flexWrap: 'wrap' as const }

/**
 * All five categories together — the set a pilot scans.
 *
 * The palette was retuned specifically so IFR and LIFR stay separable: the first
 * draft had them 0.05 apart in normalised RGB, which is two barely-different pinks
 * standing for "below minimums" and "well below minimums".
 */
export const AllCategories = () => (
  <div style={row}>
    <FlightRulesBadge rules="VFR" />
    <FlightRulesBadge rules="MVFR" />
    <FlightRulesBadge rules="IFR" />
    <FlightRulesBadge rules="LIFR" />
    <FlightRulesBadge rules="UNKNOWN" />
  </div>
)

/** The dark tone mapping — the same hues with container and foreground swapped. */
export const Dark = () => (
  <FlightPlannerTheme theme="brandDark" style={{ padding: 16 }}>
    <div style={row}>
      <FlightRulesBadge rules="VFR" />
      <FlightRulesBadge rules="MVFR" />
      <FlightRulesBadge rules="IFR" />
      <FlightRulesBadge rules="LIFR" />
      <FlightRulesBadge rules="UNKNOWN" />
    </div>
  </FlightPlannerTheme>
)

/**
 * Cockpit shares the dark set unchanged.
 *
 * A night theme may restyle the app; it may not restate the weather. A magenta
 * LIFR station rendered green would be a safety-shaped bug, not a theming one.
 */
export const Cockpit = () => (
  <FlightPlannerTheme theme="cockpit" style={{ padding: 16 }}>
    <div style={row}>
      <FlightRulesBadge rules="VFR" />
      <FlightRulesBadge rules="MVFR" />
      <FlightRulesBadge rules="IFR" />
      <FlightRulesBadge rules="LIFR" />
    </div>
  </FlightPlannerTheme>
)
