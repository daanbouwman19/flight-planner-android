import type { ReactNode } from 'react'

export interface BottomSheetProps {
  /** The sheet's heading. */
  title?: string
  /**
   * Fraction of the window the sheet occupies. Defaults to `0.9`.
   *
   * The picker takes almost the whole window because it exists to be typed into
   * and the results are the content; a short form sheet should size to its own
   * content instead.
   */
  heightFraction?: number
  /** Sized to its content rather than to `heightFraction`. */
  auto?: boolean
  children?: ReactNode
  className?: string
}

/**
 * A modal bottom sheet — the app's form and picker surface.
 *
 * Rendered **inline rather than in a portal**, so a design can show a sheet as one
 * element of a screen concept without a modal layer swallowing the artboard. Wrap
 * it in {@link ScrimOverlay} when the concept is about the modal state itself, and
 * put it inside a {@link PhoneFrame} when it is about the sheet on a screen.
 *
 * The drag handle is drawn and does nothing: a sheet's height is a real gesture in
 * the app, and a concept that implies you can drag this one would be promising
 * something a still design cannot show. See the README's note on what is
 * deliberately not mirrored.
 *
 * ```tsx
 * <BottomSheet title="Add aircraft" auto>…</BottomSheet>
 * ```
 */
export function BottomSheet({
  title,
  heightFraction = 0.9,
  auto = false,
  children,
  className,
}: BottomSheetProps) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      className={['fp-sheet', auto ? 'fp-sheet--auto' : null, className].filter(Boolean).join(' ')}
      style={auto ? undefined : { height: `${Math.round(heightFraction * 100)}%` }}
    >
      <div className="fp-sheet__handle" aria-hidden="true" />
      {title != null && <div className="fp-sheet__title fp-type-headline-small">{title}</div>}
      <div className="fp-sheet__content">{children}</div>
    </div>
  )
}

export interface TextFieldProps {
  /** The field name, floated above the value. */
  label: string
  value?: string
  /** Shown in place of a value when there is none. */
  placeholder?: string
  /** One line under the field. Drawn in the error colour when `error` is set. */
  supportingText?: string
  error?: boolean
  /** Trailing unit or affordance — `NM`, `kt`, `ft`. */
  suffix?: string
  className?: string
}

/**
 * An outlined text field, as the app's forms use it.
 *
 * A value typed into one of these is a figure more often than not, so it is set
 * tabular and left to right — a range or a runway length reordered by the
 * bidirectional algorithm under an RTL locale would be a wrong number rather than
 * an untidy one.
 */
export function TextField({
  label,
  value,
  placeholder,
  supportingText,
  error = false,
  suffix,
  className,
}: TextFieldProps) {
  const filled = value != null && value !== ''
  return (
    <div className={['fp-field', className].filter(Boolean).join(' ')}>
      <div
        className={[
          'fp-field__box',
          error ? 'fp-field__box--error' : null,
          filled ? 'fp-field__box--filled' : null,
        ]
          .filter(Boolean)
          .join(' ')}
      >
        <span className="fp-field__label fp-type-label-small">{label}</span>
        <span className="fp-field__value fp-type-body-large">
          {filled ? value : <span className="fp-field__placeholder">{placeholder ?? ''}</span>}
        </span>
        {suffix != null && <span className="fp-field__suffix fp-type-label-large">{suffix}</span>}
      </div>
      {supportingText != null && (
        <span
          className={[
            'fp-field__supporting',
            'fp-type-label-small',
            error ? 'fp-field__supporting--error' : null,
          ]
            .filter(Boolean)
            .join(' ')}
        >
          {supportingText}
        </span>
      )}
    </div>
  )
}
