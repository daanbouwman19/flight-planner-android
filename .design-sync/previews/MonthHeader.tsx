import { MonthHeader } from '@flightplanner/design-mirror'

/**
 * A sticky header over the rows it names.
 *
 * Opaque on purpose: it has to stay legible over whichever row happens to be
 * passing behind it, which a translucent surface cannot promise.
 */
export const OverRows = () => (
  <div style={{ width: 328 }}>
    <MonthHeader label="August 2026" />
    <div style={{ padding: '12px 0', color: 'var(--fp-on-surface)' }}>EHAM → EBBR</div>
    <div style={{ padding: '12px 0', color: 'var(--fp-on-surface)' }}>EHRD → EHAM</div>
    <MonthHeader label="July 2026" />
    <div style={{ padding: '12px 0', color: 'var(--fp-on-surface)' }}>LSZH → LIRF</div>
  </div>
)
