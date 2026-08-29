import type { ReactNode } from 'react'

export interface EmptyStateProps {
  /** What is absent. */
  title: string
  /** Why, or what to do about it. */
  message: string
  /**
   * The button's text. Both this and `onAction` are required to draw the button:
   * a label with no handler is a button that does nothing, and a handler with no
   * label is a button nobody can find.
   */
  actionLabel?: string
  onAction?: () => void
  /** An optional glyph, drawn in a tonal circle above the title. */
  icon?: ReactNode
  className?: string
}

/**
 * The list is empty and the app is fine.
 *
 * **Not interchangeable with {@link ErrorState}.** An empty list and a failed load
 * render identically in a naive implementation, but they are opposite situations —
 * one is the app working and waiting for the user, the other is the app having
 * failed. Using one for both teaches the user to ignore the message.
 *
 * Prefer supplying the action: "no routes yet" is a description, "no routes yet —
 * Generate" is a next step.
 *
 * ```tsx
 * <EmptyState
 *   title="No routes yet"
 *   message="Pick an aircraft and generate a few."
 *   actionLabel="Generate"
 *   onAction={() => {}}
 * />
 * ```
 */
export function EmptyState({
  title,
  message,
  actionLabel,
  onAction,
  icon,
  className,
}: EmptyStateProps) {
  return (
    <div className={['fp-state', className].filter(Boolean).join(' ')}>
      {icon != null && <div className="fp-state__icon">{icon}</div>}
      <div className="fp-state__title fp-type-title-medium">{title}</div>
      <p className="fp-state__message fp-type-body-medium">{message}</p>
      {actionLabel != null && onAction != null && (
        <button
          type="button"
          className="fp-button fp-state__action fp-type-label-large"
          onClick={onAction}
        >
          {actionLabel}
        </button>
      )}
    </div>
  )
}
