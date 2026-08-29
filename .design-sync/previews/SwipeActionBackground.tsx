import { SwipeActionBackground } from '@flightplanner/design-mirror'

const stage = {
  position: 'relative' as const,
  width: 328,
  height: 88,
  borderRadius: 20,
  overflow: 'hidden',
  background: 'var(--fp-surface-container)',
}

const check = (
  <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path d="M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z" />
  </svg>
)

const bin = (
  <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path d="M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z" />
  </svg>
)

/** Mid-drag: the reveal outruns the finger, so the action is legible before the card clears it. */
export const PartlyRevealed = () => (
  <div style={stage}>
    <SwipeActionBackground
      side="start"
      label="Mark flown"
      icon={check}
      containerColor="var(--fp-primary)"
      contentColor="var(--fp-on-primary)"
      progress={0.4}
    />
  </div>
)

/** Past the commit threshold: the icon has swelled, which is the visual half of the haptic. */
export const Committed = () => (
  <div style={stage}>
    <SwipeActionBackground
      side="end"
      label="Replace"
      icon={bin}
      containerColor="var(--fp-error)"
      contentColor="var(--fp-on-error)"
      progress={0.9}
      committed
    />
  </div>
)

/** At rest it is fully transparent, so a list at rest has no coloured bands hiding under its rows. */
export const AtRest = () => (
  <div style={stage}>
    <SwipeActionBackground
      side="start"
      label="Mark flown"
      icon={check}
      containerColor="var(--fp-primary)"
      contentColor="var(--fp-on-primary)"
      progress={0}
    />
  </div>
)
