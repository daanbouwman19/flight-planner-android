import { ConfirmationDialog, ScrimOverlay } from '@flightplanner/design-mirror'

/**
 * The dimmed layer a modal sits on.
 *
 * Separate from the dialog so a concept can show the dialog alone — the common
 * case in a screen flow — without every card acquiring a scrim. This scrim belongs
 * to a modal and never to the window's system bars, which stay genuinely empty.
 */
export const OverContent = () => (
  <ScrimOverlay>
    <ConfirmationDialog
      title="Delete this flight?"
      message="It will be removed from your logbook and from your statistics."
      confirmLabel="Delete"
    />
  </ScrimOverlay>
)
