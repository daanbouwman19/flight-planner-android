import type { CSSProperties } from 'react'

export interface ValueChipProps {
  /** The field name — short, and read as a caption. */
  label: string
  /** The figure. Always set as a figure: tabular, and left to right. */
  value: string
  /**
   * Fades the container only, for a chip drawn over the route card's map.
   * Defaults to `1`.
   *
   * The *content* is never faded with it: a chip over a map wants its coastline
   * showing faintly behind the figure, whereas a figure at 70 % is just a figure
   * that is harder to read.
   */
  containerAlpha?: number
  className?: string
}

/**
 * A labelled figure — `DIST 3,451 nm`, `RWY 12,467 ft`.
 *
 * The workhorse of every dense surface in the app. The value is set in the
 * `labelLarge` slot because that slot carries tabular figures, so a row of chips
 * stays aligned and a value that changes does not shove its neighbours sideways.
 *
 * **Label left, value right, pinned to both edges.** When the chip is sized to its
 * content this is indistinguishable from centring, and it only matters once a
 * caller stretches it — a grid of equal-width chips, say. There, centred content
 * moves left and right as the value's width changes, so `13 NM` and `2,990 NM` sit
 * at different offsets and the eye has to re-find the number on every row. Pinned,
 * the labels align down one edge and the digits down the other, which is how a
 * table of numbers has always been set.
 *
 * ```tsx
 * <ValueChip label="DIST" value="3,451 nm" />
 * <ValueChip label="RWY" value="12,467 ft" containerAlpha={0.72} />
 * ```
 */
export function ValueChip({ label, value, containerAlpha = 1, className }: ValueChipProps) {
  return (
    <span
      className={['fp-value-chip', className].filter(Boolean).join(' ')}
      style={{ '--fp-value-chip-alpha': containerAlpha } as CSSProperties}
    >
      <span className="fp-value-chip__label fp-type-label-small">{label}</span>
      <span className="fp-value-chip__value fp-type-label-large">{value}</span>
    </span>
  )
}
