export interface FlightDatePickerDialogProps {
  /** The month on show, as `year` and zero-based `month`. */
  year: number
  month: number
  /** Day of month currently selected, or `undefined` for none. */
  selectedDay?: number
  /**
   * The last selectable day of this month, or `undefined` if the whole month is
   * selectable.
   *
   * A flight cannot be logged in the future, so days past today are unselectable
   * rather than merely rejected on submit — the constraint is stated where the
   * choice is made.
   */
  maxDay?: number
  className?: string
}

const WEEKDAYS = ['M', 'T', 'W', 'T', 'F', 'S', 'S']
const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
]

/**
 * The date picker for logging a flight.
 *
 * **Future days are unselectable, not merely rejected.** A flight being logged has
 * already been flown, so the dialog states that constraint where the choice is
 * made rather than after it — a date you cannot pick teaches the rule; an error
 * message after you pick it does not.
 *
 * Weeks start on Monday, as they do everywhere else in the app.
 *
 * ```tsx
 * <FlightDatePickerDialog year={2026} month={7} selectedDay={24} maxDay={29} />
 * ```
 */
export function FlightDatePickerDialog({
  year,
  month,
  selectedDay,
  maxDay,
  className,
}: FlightDatePickerDialogProps) {
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  // `getDay()` is Sunday-first; shift so Monday is column 0.
  const firstWeekday = (new Date(year, month, 1).getDay() + 6) % 7
  const cells: Array<number | null> = [
    ...Array.from({ length: firstWeekday }, () => null),
    ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
  ]

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Select date"
      className={['fp-datepicker', className].filter(Boolean).join(' ')}
    >
      <div className="fp-datepicker__caption fp-type-label-large">Select date</div>
      <div className="fp-datepicker__headline fp-type-headline-small">
        {selectedDay != null ? `${selectedDay} ${MONTH_NAMES[month]} ${year}` : 'No date selected'}
      </div>

      <div className="fp-datepicker__month fp-type-title-small">
        {MONTH_NAMES[month]} {year}
      </div>

      <div className="fp-datepicker__grid">
        {WEEKDAYS.map((day, i) => (
          <span key={`h-${i}`} className="fp-datepicker__weekday fp-type-label-small">
            {day}
          </span>
        ))}
        {cells.map((day, i) => {
          if (day == null) return <span key={`e-${i}`} />
          const disabled = maxDay != null && day > maxDay
          return (
            <button
              key={day}
              type="button"
              disabled={disabled}
              aria-current={day === selectedDay ? 'date' : undefined}
              className={[
                'fp-datepicker__day',
                'fp-type-body-medium',
                day === selectedDay ? 'fp-datepicker__day--selected' : null,
              ]
                .filter(Boolean)
                .join(' ')}
            >
              {day}
            </button>
          )
        })}
      </div>

      <div className="fp-datepicker__actions">
        <button type="button" className="fp-button fp-button--text fp-type-label-large">
          Cancel
        </button>
        <button type="button" className="fp-button fp-button--text fp-type-label-large">
          OK
        </button>
      </div>
    </div>
  )
}
