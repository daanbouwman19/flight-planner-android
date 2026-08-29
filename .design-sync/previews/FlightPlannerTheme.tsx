import { FlightPlannerTheme, FlightRulesBadge, ValueChip } from '@flightplanner/design-mirror'

const sample = (
  <div style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: 16, width: 300 }}>
    <div className="fp-type-headline-small" style={{ color: 'var(--fp-on-background)' }}>
      EHAM → EGLL
    </div>
    <div style={{ display: 'flex', gap: 8 }}>
      <FlightRulesBadge rules="VFR" />
      <FlightRulesBadge rules="IFR" />
    </div>
    <div style={{ display: 'flex', gap: 8 }}>
      <ValueChip label="DIST" value="199 NM" />
      <ValueChip label="TIME" value="1:52" />
    </div>
    <button type="button" className="fp-button fp-type-label-large">
      Generate
    </button>
  </div>
)

/**
 * **Wrap every design in this.** It is where the design system's colours live.
 *
 * Each scheme is a block of CSS custom properties keyed on `data-fp-theme`, so a
 * component outside this wrapper resolves `var(--fp-primary)` against nothing and
 * renders unstyled — the single most common way to get a broken-looking design out
 * of this library.
 */
export const BrandLight = () => (
  <FlightPlannerTheme theme="brandLight" fullBleed>
    {sample}
  </FlightPlannerTheme>
)

export const BrandDark = () => (
  <FlightPlannerTheme theme="brandDark" fullBleed>
    {sample}
  </FlightPlannerTheme>
)

/** Cockpit: a night-flying instrument panel, not a third dark mode. */
export const Cockpit = () => (
  <FlightPlannerTheme theme="cockpit" fullBleed>
    {sample}
  </FlightPlannerTheme>
)

/** Chart: printed chart paper and navy ink, not a second light mode. */
export const Chart = () => (
  <FlightPlannerTheme theme="chart" fullBleed>
    {sample}
  </FlightPlannerTheme>
)
