import type { ReactNode } from 'react'

/**
 * The width past which a single column stops helping.
 *
 * Beyond it a filter field would be 390px wide to say "Any", and a route card's
 * map would be mostly ocean because the aspect went with it. **The extra width
 * becomes margin and the column stays a column.** A grid would use it better and
 * is a bigger change than the one this bound exists to make — so do not design one
 * without deciding to.
 */
export const MAX_CONTENT_WIDTH = 640

/** A wider bound for the detail screens and the logbook, where the density is lower. */
export const WIDE_MAX_CONTENT_WIDTH = 840

export interface TabletFrameProps {
  /** The clock shown in the status bar. Defaults to `8:04`. */
  time?: string
  /** The {@link NavigationRail} — pinned to the leading edge, never scrolling. */
  rail?: ReactNode
  /** Frame width in px. Defaults to `1280`, the tablet breakpoint the app previews at. */
  width?: number
  /** Frame height in px. Defaults to `800`. */
  height?: number
  children?: ReactNode
  className?: string
}

/**
 * A 1280 × 800 tablet, drawn the way this app occupies one.
 *
 * The same invariant as the phone holds and is easier to break here: **the system
 * bars stay empty**, and content runs under the clock. What changes is the
 * navigation — a rail on the leading edge instead of a bar along the bottom — and
 * that the content is width-capped rather than stretched.
 *
 * ```tsx
 * <TabletFrame rail={<NavigationRail selected="plan" />}>…</TabletFrame>
 * ```
 */
export function TabletFrame({
  time = '8:04',
  rail,
  width = 1280,
  height = 800,
  children,
  className,
}: TabletFrameProps) {
  return (
    <div
      className={['fp-tablet-frame', className].filter(Boolean).join(' ')}
      style={{ width, height }}
    >
      <div className="fp-tablet-frame__status">
        <span>{time}</span>
        <span>▪ ▪ ▮</span>
      </div>
      <div className="fp-tablet-frame__body">
        {rail}
        <div className="fp-tablet-frame__content">{children}</div>
      </div>
    </div>
  )
}

export interface TwoPaneScaffoldProps {
  /** The list, on the leading side. */
  list: ReactNode
  /**
   * The detail, on the trailing side.
   *
   * On a phone this is a separate screen the list navigates to; here both are on
   * screen at once, which is the whole point of the wide layout.
   */
  detail: ReactNode
  className?: string
}

/**
 * List on the leading side, detail on the trailing side.
 *
 * The wide form of Plan, Fleet and Logbook. On a phone these are two screens and a
 * navigation; here they are one screen, and selecting a row replaces the detail
 * pane **in place** — which is why a pane change is a fade-through and never a
 * slide. A slide would claim a direction the selection does not have.
 */
export function TwoPaneScaffold({ list, detail, className }: TwoPaneScaffoldProps) {
  return (
    <div className={['fp-two-pane', className].filter(Boolean).join(' ')}>
      <div className="fp-two-pane__list">{list}</div>
      <div className="fp-two-pane__detail">{detail}</div>
    </div>
  )
}
