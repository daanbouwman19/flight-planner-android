import { NavigationRail } from '@flightplanner/design-mirror'

const stage = { height: 520, display: 'flex', background: 'var(--fp-background)' }

/**
 * The rail a wide window gets instead of the bottom bar.
 *
 * **It carries five destinations where the bar carries four.** Settings is
 * somewhere you go once and come back from, so on a phone it lives in the app bar
 * rather than taking a fifth of the bar; a rail has room, so it gets a permanent
 * home. That is a real difference between the two layouts, not a detail.
 *
 * It also never auto-hides. The bar retracts as you scroll because a horizontal
 * bar is the only form that gives height back — a rail costs width, and width is
 * what a wide window has spare.
 */
export const Plan = () => (
  <div style={stage}>
    <NavigationRail selected="plan" />
  </div>
)

export const Logbook = () => (
  <div style={stage}>
    <NavigationRail selected="logbook" />
  </div>
)

/** Settings selected — reachable directly here, unlike on a phone. */
export const Settings = () => (
  <div style={stage}>
    <NavigationRail selected="settings" />
  </div>
)
