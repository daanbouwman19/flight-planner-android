import type { ReactNode } from 'react'

export interface ConfirmationDialogProps {
  /** What is about to happen. */
  title: string
  /** The consequence, stated plainly. */
  message: string
  /** The affirmative button's text — name the action, never "OK". */
  confirmLabel: string
  onConfirm?: () => void
  onDismiss?: () => void
  /** The dismissive button's text. Defaults to `Cancel`. */
  cancelLabel?: string
  className?: string
}

/**
 * A two-button alert, for an action worth stopping to confirm.
 *
 * Rendered inline rather than in a portal, so a design can show the dialog as one
 * element of a screen concept without a modal layer swallowing the artboard. Wrap
 * it in {@link ScrimOverlay} when the concept is about the modal state itself.
 *
 * ```tsx
 * <ConfirmationDialog
 *   title="Delete this flight?"
 *   message="It will be removed from your logbook and your statistics."
 *   confirmLabel="Delete"
 * />
 * ```
 */
export function ConfirmationDialog({
  title,
  message,
  confirmLabel,
  onConfirm,
  onDismiss,
  cancelLabel = 'Cancel',
  className,
}: ConfirmationDialogProps) {
  return (
    <div
      role="alertdialog"
      aria-modal="true"
      aria-label={title}
      className={['fp-dialog', className].filter(Boolean).join(' ')}
    >
      <div className="fp-dialog__title fp-type-headline-small">{title}</div>
      <p className="fp-dialog__message fp-type-body-medium">{message}</p>
      <div className="fp-dialog__actions">
        <button
          type="button"
          className="fp-button fp-button--text fp-type-label-large"
          onClick={onDismiss}
        >
          {cancelLabel}
        </button>
        <button
          type="button"
          className="fp-button fp-button--text fp-type-label-large"
          onClick={onConfirm}
        >
          {confirmLabel}
        </button>
      </div>
    </div>
  )
}

export interface ScrimOverlayProps {
  children?: ReactNode
  className?: string
}

/**
 * The dimmed layer a modal sits on.
 *
 * Separate from {@link ConfirmationDialog} so a concept can show the dialog alone
 * — the common case in a screen flow — without every card acquiring a scrim.
 */
export function ScrimOverlay({ children, className }: ScrimOverlayProps) {
  return (
    <div className={['fp-scrim', className].filter(Boolean).join(' ')}>{children}</div>
  )
}
