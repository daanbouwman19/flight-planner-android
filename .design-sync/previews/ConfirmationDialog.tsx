import { ConfirmationDialog } from '@flightplanner/design-mirror'

/** A destructive action named in its own button rather than confirmed with "OK". */
export const DeleteFlight = () => (
  <ConfirmationDialog
    title="Delete this flight?"
    message="It will be removed from your logbook and from your statistics."
    confirmLabel="Delete"
  />
)

/** A non-destructive confirmation. */
export const ReplaceRoute = () => (
  <ConfirmationDialog
    title="Replace this route?"
    message="A new route will be generated for the same aircraft."
    confirmLabel="Replace"
    cancelLabel="Keep"
  />
)
