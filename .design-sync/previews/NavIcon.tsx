import { NavIcon } from '@flightplanner/design-mirror'

/**
 * The app's five icons, as the exact vector paths it ships.
 *
 * A paper plane for Plan and an airframe for Fleet say more than two generic
 * glyphs would — which is why they were drawn rather than pulled from a set.
 */
export const AllIcons = () => (
  <div style={{ display: 'flex', gap: 20, color: 'var(--fp-on-surface)' }}>
    <NavIcon name="plan" size={32} />
    <NavIcon name="fleet" size={32} />
    <NavIcon name="logbook" size={32} />
    <NavIcon name="stats" size={32} />
    <NavIcon name="settings" size={32} />
  </div>
)

/** Tinted and sized as a screen would use them. */
export const InContext = () => (
  <div style={{ display: 'flex', gap: 20, alignItems: 'center', color: 'var(--fp-primary)' }}>
    <NavIcon name="plan" size={20} />
    <NavIcon name="plan" size={24} />
    <NavIcon name="plan" size={40} />
  </div>
)
