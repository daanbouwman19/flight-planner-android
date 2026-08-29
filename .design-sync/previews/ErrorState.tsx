import { ErrorState } from '@flightplanner/design-mirror'

/**
 * The load failed — the opposite situation from an empty list.
 *
 * Rendering the two identically teaches the user to ignore the message, which is
 * why these are separate components rather than one with a flag.
 */
export const WithRetry = () => (
  <div style={{ width: 328 }}>
    <ErrorState
      title="Couldn't load weather"
      message="The station didn't answer. Your route is unaffected."
      onRetry={() => {}}
    />
  </div>
)

/** Nothing to retry: the failure is not transient. */
export const Terminal = () => (
  <div style={{ width: 328 }}>
    <ErrorState
      title="Airport data is out of date"
      message="Reinstall the app to pick up the current dataset."
    />
  </div>
)
