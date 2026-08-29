export interface ModeOption {
  /** The chip's text. */
  label: string
  /** Drawn after the label as `label · count`, and it is drawn, not merely spoken. */
  count?: number
  /** Overrides what a screen reader announces for this option. */
  contentDescription?: string
  /** Defaults to `true`. */
  enabled?: boolean
}

export interface ModeSelectorProps {
  options: ModeOption[]
  /** Index into `options`. Exactly one option is always selected. */
  selectedIndex: number
  onSelect?: (index: number) => void
  className?: string
}

/**
 * Single-select chips in a wrapping row — the app's mode/filter switch.
 *
 * **It is deliberately not a segmented button.** A segmented row divides the width
 * equally, so every option is as wide as the longest one needs and none is as wide
 * as it wants: `Not flown · 116` did not fit a third of a 360 dp phone and
 * ellipsised to a *wrong number*, which is why the count used to be spoken to a
 * screen reader and never drawn. Chips are sized to their own label, so the count
 * is visible, and the row **wraps** at large font sizes where a segmented row
 * truncates.
 *
 * Re-tapping the active option is a no-op rather than a deselect: every caller
 * needs exactly one mode to be true.
 *
 * ```tsx
 * <ModeSelector
 *   options={[{ label: 'All' }, { label: 'Not flown', count: 116 }, { label: 'This aircraft' }]}
 *   selectedIndex={1}
 * />
 * ```
 */
export function ModeSelector({ options, selectedIndex, onSelect, className }: ModeSelectorProps) {
  return (
    <div
      className={['fp-mode-selector', className].filter(Boolean).join(' ')}
      role="radiogroup"
    >
      {options.map((option, index) => {
        const selected = index === selectedIndex
        return (
          <button
            key={`${option.label}-${index}`}
            type="button"
            role="radio"
            aria-checked={selected}
            aria-label={option.contentDescription}
            disabled={option.enabled === false}
            className={[
              'fp-mode-selector__chip',
              'fp-type-label-large',
              selected ? 'fp-mode-selector__chip--selected' : null,
            ]
              .filter(Boolean)
              .join(' ')}
            onClick={() => {
              if (!selected) onSelect?.(index)
            }}
          >
            {option.count != null ? `${option.label} · ${option.count}` : option.label}
          </button>
        )
      })}
    </div>
  )
}
