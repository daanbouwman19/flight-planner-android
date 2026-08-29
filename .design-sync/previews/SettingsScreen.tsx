import { FlightPlannerTheme, SettingsScreen } from '@flightplanner/design-mirror'

/** The default state: follow the system, dynamic colour on, nautical units. */
export const Default = () => <SettingsScreen />

/**
 * Cockpit selected, and the dynamic-colour switch off with it.
 *
 * Cockpit ignores dynamic colour by construction: the point of the theme is that
 * the screen stops competing with the pilot's dark adaptation, and a scheme
 * derived from whatever the wallpaper happens to be cannot promise that.
 */
export const CockpitSelected = () => (
  <FlightPlannerTheme theme="cockpit">
    <SettingsScreen theme={3} units={0} weatherProvider={1} />
  </FlightPlannerTheme>
)

/** Chart selected — the same exemption, for the same reason, on the light side. */
export const ChartSelected = () => (
  <FlightPlannerTheme theme="chart">
    <SettingsScreen theme={4} units={1} icaoOnly />
  </FlightPlannerTheme>
)
