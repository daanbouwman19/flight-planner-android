export interface MonthHeaderProps {
  /** The month the rows beneath belong to — `August 2026`. */
  label: string
  className?: string
}

/**
 * A sticky section header naming the month the rows beneath it belong to.
 *
 * **Opaque, unlike the rest of this app's chrome, and that is not a violation of
 * the transparent-bars rule.** That rule is about the *window's* chrome — nothing
 * may be painted behind the status and navigation bars. A sticky header inside a
 * scrolling list is a different object: it has to stay legible over whichever row
 * happens to be passing behind it, which a translucent surface cannot promise for
 * an arbitrary row's colours.
 */
export function MonthHeader({ label, className }: MonthHeaderProps) {
  return (
    <div className={['fp-month-header', 'fp-type-label-large', className].filter(Boolean).join(' ')}>
      {label}
    </div>
  )
}
