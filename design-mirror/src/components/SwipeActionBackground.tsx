import type { CSSProperties, ReactNode } from 'react'

export type SwipeActionSide = 'start' | 'end'

export interface SwipeActionBackgroundProps {
  /** Which edge the action is revealed from. */
  side: SwipeActionSide
  /** The glyph, drawn at 24px. */
  icon?: ReactNode
  /** The action's name. Drawn on the `end` side only, before the icon. */
  label: string
  /**
   * The container's colour — pass a **solid** role, not a container role.
   *
   * A pale container behind a `surfaceContainer` card is two neighbouring greys
   * and the reveal stays invisible until it is nearly complete.
   */
  containerColor: string
  /** The icon and label colour, paired with `containerColor` by the caller. */
  contentColor: string
  /** How far the card has travelled: 0 at rest, 1 fully swiped. Defaults to `1`. */
  progress?: number
  /** True once releasing would commit the action. Defaults to `false`. */
  committed?: boolean
  className?: string
}

/** Reveal outruns the drag, so the action is legible before the card is clear of it. */
const revealRate = 1.5
const restingScale = 0.7
const commitOvershoot = 0.15

/**
 * The coloured surface revealed behind a card being swiped.
 *
 * **It takes colours rather than choosing them.** "Confirm" and "destroy" are the
 * caller's semantics, and there is deliberately no hardcoded green anywhere in it —
 * a fixed hue would be the one element on screen that ignores the Cockpit theme.
 *
 * **`progress` and `committed` are separate on purpose.** `progress` tracks the
 * finger with no spring, because a spring lags and the surface must feel attached
 * to the drag. `committed` is a discrete event and gets a sprung response: the icon
 * swells and settles, which is the visual half of the haptic firing at the same
 * moment.
 *
 * At rest it is fully transparent, so a list at rest has no coloured bands hiding
 * under its rows.
 *
 * ```tsx
 * <SwipeActionBackground
 *   side="end"
 *   label="Delete"
 *   containerColor="var(--fp-error)"
 *   contentColor="var(--fp-on-error)"
 *   progress={0.6}
 * />
 * ```
 */
export function SwipeActionBackground({
  side,
  icon,
  label,
  containerColor,
  contentColor,
  progress = 1,
  committed = false,
  className,
}: SwipeActionBackgroundProps) {
  const drag = Math.min(Math.max(progress, 0), 1)
  const reveal = Math.min(drag * revealRate, 1)
  const scale = restingScale + (1 - restingScale) * reveal + (committed ? commitOvershoot : 0)

  return (
    <div
      aria-hidden="true"
      className={[
        'fp-swipe-action',
        `fp-swipe-action--${side}`,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      style={
        {
          background: containerColor,
          color: contentColor,
          opacity: reveal,
        } as CSSProperties
      }
    >
      <div className="fp-swipe-action__content">
        {side === 'end' && <span className="fp-type-label-large">{label}</span>}
        <span
          className={[
            'fp-swipe-action__icon',
            committed ? 'fp-swipe-action__icon--committed' : null,
          ]
            .filter(Boolean)
            .join(' ')}
          style={{ transform: `scale(${scale.toFixed(3)})` }}
        >
          {icon}
        </span>
        {side === 'start' && <span className="fp-type-label-large">{label}</span>}
      </div>
    </div>
  )
}
