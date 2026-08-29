import { ModeSelector } from '@flightplanner/design-mirror'

/**
 * The Plan screen's three modes, with the count drawn rather than only spoken.
 *
 * A segmented row gave every option an equal third of the width, and
 * "Not flown · 116" ellipsised to a wrong number — which is worse than none.
 */
export const WithCounts = () => (
  <ModeSelector
    options={[{ label: 'All' }, { label: 'Not flown', count: 116 }, { label: 'This aircraft' }]}
    selectedIndex={1}
  />
)

/** A disabled option: nothing to filter to, so the chip cannot be chosen. */
export const WithDisabled = () => (
  <ModeSelector
    options={[{ label: 'All' }, { label: 'Not flown', count: 0, enabled: false }, { label: 'This aircraft' }]}
    selectedIndex={0}
  />
)

/** Five options in a narrow column, showing the row wrap that a segmented row cannot do. */
export const Wrapping = () => (
  <div style={{ width: 260 }}>
    <ModeSelector
      options={[
        { label: 'System' },
        { label: 'Light' },
        { label: 'Dark' },
        { label: 'Cockpit' },
        { label: 'Chart' },
      ]}
      selectedIndex={3}
    />
  </div>
)
