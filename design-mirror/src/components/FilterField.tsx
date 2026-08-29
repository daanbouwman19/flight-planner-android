export interface FilterFieldProps {
  /** The field name — drawn in letter-spaced caps. */
  label: string
  /** The short identifier the filter is currently set to. */
  value: string
  /** Whether a constraint is actually set. */
  selected: boolean
  onClick?: () => void
  /**
   * One line saying what the value *means* — `3,010 NM · 6,900 ft`.
   *
   * This is the point of the component. A chip can say what is set; a field can
   * say what that does. Unlike the route card's flight-rules slot it holds no
   * space for a detail it does not have, because the user's own tap is what
   * changes it.
   */
  detail?: string
  className?: string
}

/**
 * A filter control, set like the data it filters.
 *
 * A letter-spaced caps label over a short identifier in tabular figures, over one
 * line of detail. Outline means unset and a filled container means set — a language
 * it shares with {@link ModeSelector} — and both survive being drawn on any surface.
 *
 * Put a pair inside a row of equal-height items to keep them level.
 *
 * ```tsx
 * <FilterField label="AIRCRAFT" value="PA-28" selected detail="3,010 NM · 6,900 ft" />
 * <FilterField label="DEPARTURE" value="Any" selected={false} />
 * ```
 */
export function FilterField({
  label,
  value,
  selected,
  onClick,
  detail,
  className,
}: FilterFieldProps) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={[
        'fp-filter-field',
        selected ? 'fp-filter-field--selected' : null,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <span className="fp-filter-field__label fp-type-label-small">{label}</span>
      <span className="fp-filter-field__value fp-type-title-medium">{value}</span>
      {detail != null && (
        <span className="fp-filter-field__detail fp-type-label-small">{detail}</span>
      )}
    </button>
  )
}
