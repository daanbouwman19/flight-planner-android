/**
 * The one plate every mark this app makes over the globe is made on:
 * `surfaceContainer` at this alpha, an `extraSmall` corner, one hairline of
 * `outlineVariant`. `PlateAlpha` in `:feature:globe`'s `GlobeControls.kt`.
 *
 * A host drawing its own chrome over the imagery — the route detail's app bar —
 * makes the *same* plate, not a similar one, which is why this is exported.
 */
export const GLOBE_PLATE_ALPHA = 0.82

/** The glass row height, and the floor for a control on a moving surface. `ControlSize`. */
export const GLOBE_CONTROL_SIZE = 44

export interface GlobeCameraControlsProps {
  onZoomIn?: () => void
  onZoomOut?: () => void
  onRefit?: () => void
  /**
   * The camera's heading, degrees clockwise from north. Passing it (i.e. the
   * view has been turned) grows the compass cell — a needle over the bearing as
   * a figure, which is a readout and a "face north" control at once. The app's
   * still-north view never shows it; a concept about the turned state can.
   */
  bearingDegrees?: number
  onResetNorth?: () => void
  className?: string
}

/**
 * The camera controls, as one instrument rather than four buttons.
 *
 * Ported from `GlobeCameraControls` in `:feature:globe`. The zoom marks are
 * **type, not icons** — a plus and a minus drawn at the app's hairline weight,
 * because there is nothing in a stock `zoom_in` vector a cross does not say, and
 * the vector said it in another product's hand. "Re-fit" is four corner
 * brackets: a crop mark, not a crosshair, because the control re-frames the
 * whole leg rather than centring on a point.
 *
 * In the mirror the buttons are drawn and inert, the same way `BottomSheet`'s
 * drag handle is — the globe here does not move. Handlers are accepted so a
 * prototype can wire them.
 */
export function GlobeCameraControls({
  onZoomIn,
  onZoomOut,
  onRefit,
  bearingDegrees,
  onResetNorth,
  className,
}: GlobeCameraControlsProps) {
  const rotated = bearingDegrees != null
  return (
    <div className={['fp-globe-plate', className].filter(Boolean).join(' ')}>
      <button type="button" className="fp-globe-plate__cell" onClick={onZoomIn} aria-label="Zoom in">
        <ZoomMark withVertical />
      </button>
      <span className="fp-globe-plate__rule" />
      <button type="button" className="fp-globe-plate__cell" onClick={onZoomOut} aria-label="Zoom out">
        <ZoomMark />
      </button>
      <span className="fp-globe-plate__rule" />
      <button type="button" className="fp-globe-plate__cell" onClick={onRefit} aria-label="Frame the route">
        <FrameBrackets />
      </button>
      {rotated && (
        <>
          <span className="fp-globe-plate__rule" />
          <button
            type="button"
            className="fp-globe-plate__heading"
            onClick={onResetNorth}
            aria-label="Face north"
          >
            <Needle degrees={bearingDegrees} />
            <span className="fp-globe-plate__bearing fp-type-label-small">
              {normalisedDegrees(bearingDegrees)}°
            </span>
          </button>
        </>
      )}
    </div>
  )
}

export interface GlobeAttributionProps {
  /** Defaults to the string `:feature:globe`'s `GlobeImagery.Attribution` carries. */
  text?: string
  className?: string
}

/**
 * The imagery credit. Drawn in every layout because the provider requires it,
 * hidden from accessibility because it is a licence notice — it appears in
 * Settings · About in reading order for anyone going looking. `GlobeAttribution`.
 */
export function GlobeAttribution({ text = 'Imagery © NASA GIBS', className }: GlobeAttributionProps) {
  return (
    <span
      className={['fp-globe-attribution', 'fp-type-label-small', className].filter(Boolean).join(' ')}
      aria-hidden="true"
    >
      {text}
    </span>
  )
}

/** A plus, or its horizontal bar alone. Two strokes at the app's hairline weight. */
function ZoomMark({ withVertical = false }: { withVertical?: boolean }) {
  return (
    <svg viewBox="0 0 18 18" className="fp-globe-mark" aria-hidden="true">
      <line x1="2" y1="9" x2="16" y2="9" />
      {withVertical && <line x1="9" y1="2" x2="9" y2="16" />}
    </svg>
  )
}

/** Four corner brackets — what "frame the route" means on a chart. */
function FrameBrackets() {
  const a = 18 * 0.32
  return (
    <svg viewBox="0 0 18 18" className="fp-globe-mark" aria-hidden="true">
      <path
        d={
          `M1,${1 + a} L1,1 L${1 + a},1 ` +
          `M${17 - a},1 L17,1 L17,${1 + a} ` +
          `M17,${17 - a} L17,17 L${17 - a},17 ` +
          `M${1 + a},17 L1,17 L1,${17 - a}`
        }
        fill="none"
      />
    </svg>
  )
}

/** The two-tone compass card, pointing up when the globe faces north. */
function Needle({ degrees }: { degrees: number }) {
  return (
    <svg viewBox="0 0 16 16" className="fp-globe-needle" style={{ transform: `rotate(${-degrees}deg)` }} aria-hidden="true">
      <path d="M8,0 L5.44,8 L10.56,8 Z" className="fp-globe-needle__n" />
      <path d="M8,16 L5.44,8 L10.56,8 Z" className="fp-globe-needle__s" />
    </svg>
  )
}

/** Degrees clockwise from north, wrapped into 0–359 and zero-padded for display. */
function normalisedDegrees(deg: number): string {
  const wrapped = ((Math.round(deg) % 360) + 360) % 360
  return String(wrapped).padStart(3, '0')
}
