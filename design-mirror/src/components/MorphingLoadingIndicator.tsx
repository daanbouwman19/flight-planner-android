import { tokens } from '../tokens/tokens.gen'

export interface MorphingLoadingIndicatorProps {
  /** Draws the indicator on a tonal container. Defaults to `false`. */
  contained?: boolean
  /** What a screen reader announces. Defaults to `Loading`. */
  contentDescription?: string
  /** Diameter in px. Defaults to `48`. */
  size?: number
  className?: string
}

/**
 * The app's busy indicator: a shape that morphs through a short sequence.
 *
 * **The four shapes are the real Material 3 Expressive polygons**, exported from
 * the Android design system as SVG path data rather than redrawn — a hand-drawn
 * nine-lobed cookie is a different cookie. The sequence is Circle → Cookie →
 * Clover → VerySunny: four shapes rather than Material's default seven, because a
 * short sequence reads as one gesture, which suits a loading state that usually
 * lasts a few hundred milliseconds.
 *
 * A loading indicator is one of the few purely decorative things that must still
 * be announced: it is the only signal a screen-reader user gets that the screen is
 * busy rather than empty.
 *
 * ```tsx
 * <MorphingLoadingIndicator />
 * <MorphingLoadingIndicator contained />
 * ```
 */
export function MorphingLoadingIndicator({
  contained = false,
  contentDescription = 'Loading',
  size = 48,
  className,
}: MorphingLoadingIndicatorProps) {
  const inner = Math.round(size * (contained ? 0.5 : 0.8))
  return (
    <div
      role="progressbar"
      aria-label={contentDescription}
      className={[
        'fp-loading',
        contained ? 'fp-loading--contained' : null,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      style={{ width: size, height: size }}
    >
      <svg
        className="fp-loading__shape"
        width={inner}
        height={inner}
        viewBox="0 0 1 1"
        aria-hidden="true"
      >
        {/*
          CSS cannot tween between two `d` values of differing structure, so the
          sequence is a crossfade-and-rotate through the four real polygons rather
          than a true vertex morph. The Android original morphs vertex to vertex.
        */}
        {shapeOrder.map((name, index) => (
          <path
            key={name}
            d={tokens.materialShapes[name]}
            fill="currentColor"
            className="fp-loading__path"
            style={{ animationDelay: `${(index * cycleMs) / shapeOrder.length}ms` }}
          />
        ))}
      </svg>
    </div>
  )
}

const shapeOrder = ['circle', 'cookie', 'clover', 'verySunny'] as const
const cycleMs = 1600
