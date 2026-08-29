import { EmptyState } from '@flightplanner/design-mirror'

/**
 * The list is empty and the app is fine.
 *
 * "No routes yet" is a description; "no routes yet — Generate" is a next step,
 * which is why both halves of the action are supplied.
 */
export const WithAction = () => (
  <div style={{ width: 328 }}>
    <EmptyState
      title="No routes yet"
      message="Pick an aircraft and generate a few to get started."
      actionLabel="Generate"
      onAction={() => {}}
    />
  </div>
)

/** No action available — a statement rather than an invitation. */
export const MessageOnly = () => (
  <div style={{ width: 328 }}>
    <EmptyState
      title="Nothing logged this month"
      message="Flights you mark as flown will appear here."
    />
  </div>
)

/** With a glyph in its tonal circle. */
export const WithIcon = () => (
  <div style={{ width: 328 }}>
    <EmptyState
      title="No airports match"
      message="Try a shorter query, or clear the hard-surface filter."
      actionLabel="Clear filters"
      onAction={() => {}}
      icon={
        <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z" />
        </svg>
      }
    />
  </div>
)
