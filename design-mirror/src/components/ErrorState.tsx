export interface ErrorStateProps {
  /** What failed. Drawn in the error colour. */
  title: string
  /** What the reader can do about it. */
  message: string
  /** Draws a tonal Retry button when supplied. */
  onRetry?: () => void
  /** The retry button's text. Defaults to `Retry`. */
  retryLabel?: string
  className?: string
}

/**
 * The load failed.
 *
 * **Not interchangeable with {@link EmptyState}** — see that component for why the
 * distinction is load-bearing rather than cosmetic. This one is a polite live
 * region, so a screen-reader user learns the load failed even when focus is
 * elsewhere.
 *
 * ```tsx
 * <ErrorState
 *   title="Couldn't load weather"
 *   message="The station didn't answer. Your route is unaffected."
 *   onRetry={() => {}}
 * />
 * ```
 */
export function ErrorState({
  title,
  message,
  onRetry,
  retryLabel = 'Retry',
  className,
}: ErrorStateProps) {
  return (
    <div
      className={['fp-state', className].filter(Boolean).join(' ')}
      role="status"
      aria-live="polite"
    >
      <div className="fp-state__title fp-state__title--error fp-type-title-medium">{title}</div>
      <p className="fp-state__message fp-type-body-medium">{message}</p>
      {onRetry != null && (
        <button
          type="button"
          className="fp-button fp-button--tonal fp-state__action fp-type-label-large"
          onClick={onRetry}
        >
          {retryLabel}
        </button>
      )}
    </div>
  )
}
