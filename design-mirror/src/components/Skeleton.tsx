import type { CSSProperties } from 'react'

export interface SkeletonBoxProps {
  /** CSS width. Defaults to `100%`. */
  width?: string
  /** CSS height. Defaults to `14px`. */
  height?: string
  /** Corner radius, from the shape scale. Defaults to `small`. */
  radius?: 'extraSmall' | 'small' | 'medium' | 'large'
  className?: string
}

const radiusVar = {
  extraSmall: 'var(--fp-shape-extra-small)',
  small: 'var(--fp-shape-small)',
  medium: 'var(--fp-shape-medium)',
  large: 'var(--fp-shape-large)',
} as const

/**
 * One shimmering bar of a loading placeholder.
 *
 * Skeletons beat a spinner when the content's shape is known, because they stop
 * the layout jumping. They are *worse* than a spinner when you are guessing at the
 * size. The shimmer is a metronome rather than a spring — a spring would make the
 * pulse uneven — and it is deliberately slow, because a fast shimmer reads as an
 * error condition rather than as waiting. It switches off entirely under reduced
 * motion rather than merely shortening, being an infinite animation.
 */
export function SkeletonBox({
  width = '100%',
  height = '14px',
  radius = 'small',
  className,
}: SkeletonBoxProps) {
  return (
    <div
      aria-hidden="true"
      className={['fp-skeleton-box', className].filter(Boolean).join(' ')}
      style={{ width, height, borderRadius: radiusVar[radius] } as CSSProperties}
    />
  )
}

export interface SkeletonCardProps {
  className?: string
}

/**
 * The loading stand-in for one route or airport card.
 *
 * Three bars in the proportions of a real card — a title, a subtitle and a short
 * value line — so the list does not reflow when the data lands.
 */
export function SkeletonCard({ className }: SkeletonCardProps) {
  return (
    <div
      aria-hidden="true"
      className={['fp-skeleton-card', className].filter(Boolean).join(' ')}
    >
      <SkeletonBox width="55%" height="20px" />
      <SkeletonBox width="80%" height="14px" />
      <SkeletonBox width="35%" height="14px" />
    </div>
  )
}
