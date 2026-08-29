import { NavIcon, TopAppBar } from '@flightplanner/design-mirror'

/**
 * A screen title with an action.
 *
 * Transparent by design: content scrolls up under it, and nothing is painted
 * behind the status bar above it.
 */
export const WithAction = () => (
  <div style={{ width: 360, background: 'var(--fp-background)' }}>
    <TopAppBar
      title="Plan"
      action={
        <button type="button" className="fp-app-bar__action" aria-label="Settings">
          <NavIcon name="settings" />
        </button>
      }
    />
  </div>
)

/** A detail screen, with a back arrow. */
export const WithBack = () => (
  <div style={{ width: 360, background: 'var(--fp-background)' }}>
    <TopAppBar title="EHAM → EGLL" onBack={() => {}} />
  </div>
)
