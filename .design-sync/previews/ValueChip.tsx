import { ValueChip } from '@flightplanner/design-mirror'

/** The everyday case: a row of figures sized to their own content. */
export const Figures = () => (
  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
    <ValueChip label="DIST" value="3,451 nm" />
    <ValueChip label="TIME" value="07:12" />
    <ValueChip label="RWY" value="12,467 ft" />
  </div>
)

/**
 * Stretched into a grid — the arrangement the SpaceBetween exists for.
 *
 * Centred, the value would move left and right as its width changed, so "13 NM"
 * and "2,990 NM" would sit at different offsets and the eye would have to re-find
 * the number on every row. Pinned to both edges, the labels align down one edge
 * and the digits down the other.
 */
export const StretchedGrid = () => (
  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, width: 320 }}>
    <ValueChip label="DIST" value="13 NM" />
    <ValueChip label="DIST" value="2,990 NM" />
    <ValueChip label="TIME" value="0:11" />
    <ValueChip label="TIME" value="14:38" />
  </div>
)

/** Translucent, for a chip drawn over the route card's map. */
export const OverAMap = () => (
  <div
    style={{
      padding: 16,
      borderRadius: 20,
      background: 'var(--fp-surface-container)',
      display: 'flex',
      gap: 8,
      width: 320,
    }}
  >
    <ValueChip label="DIST" value="199 NM" containerAlpha={0.7} />
    <ValueChip label="TIME" value="1:52" containerAlpha={0.7} />
  </div>
)
