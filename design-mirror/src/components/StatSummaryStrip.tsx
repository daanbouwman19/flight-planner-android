export interface StatTile {
  /** The caption under the figure. */
  label: string
  /** The figure itself, already formatted. */
  value: string
  /** Overrides what a screen reader announces for the tile. */
  contentDescription?: string
}

export interface StatSummaryStripProps {
  tiles: StatTile[]
  className?: string
}

/**
 * A few large centred figures under their captions — how a statistics screen
 * states a headline.
 *
 * **It is not {@link ValueChip} stretched.** A chip is a small label-left,
 * value-right unit sized to sit over a route card's map; a summary strip wants the
 * opposite grammar. In the app each tile counts up once when it appears or changes,
 * which is the motion principle's own example of an animation worth having: it
 * draws the eye to a value that changed.
 *
 * ```tsx
 * <StatSummaryStrip
 *   tiles={[
 *     { label: 'FLIGHTS', value: '42' },
 *     { label: 'NM', value: '48,213' },
 *     { label: 'HOURS', value: '63:25' },
 *   ]}
 * />
 * ```
 */
export function StatSummaryStrip({ tiles, className }: StatSummaryStripProps) {
  return (
    <div className={['fp-stat-strip', className].filter(Boolean).join(' ')}>
      {tiles.map((tile, index) => (
        <div
          key={`${tile.label}-${index}`}
          className="fp-stat-strip__tile"
          aria-label={tile.contentDescription}
        >
          <span className="fp-stat-strip__value fp-type-title-large">{tile.value}</span>
          <span className="fp-stat-strip__label fp-type-label-small">{tile.label}</span>
        </div>
      ))}
    </div>
  )
}
