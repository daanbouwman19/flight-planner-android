import { NavigationBar } from '@flightplanner/design-mirror'

/**
 * Each destination selected in turn.
 *
 * There is one icon per entry rather than a filled/outlined pair: Material signals
 * the current destination with the active indicator behind the icon, so a second
 * glyph would restate what the indicator already says.
 */
export const Plan = () => (
  <div style={{ width: 360 }}>
    <NavigationBar selected="plan" />
  </div>
)

export const Fleet = () => (
  <div style={{ width: 360 }}>
    <NavigationBar selected="fleet" />
  </div>
)

export const Logbook = () => (
  <div style={{ width: 360 }}>
    <NavigationBar selected="logbook" />
  </div>
)

export const Stats = () => (
  <div style={{ width: 360 }}>
    <NavigationBar selected="stats" />
  </div>
)
