export interface AircraftWidgetReady {
  status: 'ready'
  /** Already formatted for the reader's locale, weekday dropped — `16 Sep`. */
  date: string
  /** `Boeing 787-9` — the airframe's full display name; the card ellipsises it, never shortens it. */
  name: string
  /** The aircraft type code, `B789`. The card's anchor line. */
  typeCode: string
  flown: boolean
  /** Already formatted, in the unit the user chose — `7,355 NM`. */
  rangeText: string
  /**
   * The runway this airframe needs, already formatted — `9,800 ft` — or
   * `null` when it names no takeoff distance at all (a glider). The wide
   * layouts then show the range alone rather than `RWY 0 ft`.
   */
  runwayText: string | null
}

export interface AircraftWidgetFleetEmpty {
  status: 'fleetEmpty'
}

export interface AircraftWidgetUnavailable {
  status: 'unavailable'
}

export type AircraftWidgetState = AircraftWidgetReady | AircraftWidgetFleetEmpty | AircraftWidgetUnavailable

export interface AircraftWidgetCardProps {
  state: AircraftWidgetState
  /**
   * The four-cell layout: the runway figure joins the range, and the flown
   * status is stated in words. Below the real widget's 220 dp threshold the
   * two-cell layout keeps the range alone and states the status as a dot.
   * Default `false` — the widget is two cells by default.
   */
  wide?: boolean
  /**
   * The two-row layout: the challenge card's three bands, with an empty band
   * where that card draws its map so the two cards' base lines meet. Default
   * `false` — one row is the whole fact, which is why the widget is one row
   * by default.
   */
  tall?: boolean
  className?: string
}

/**
 * "Aircraft of the day" — the second home-screen widget, the same object as
 * `ChallengeWidgetCard` about a different fact.
 *
 * Ported from `AircraftWidget.kt`'s `AircraftContent` and the grammar both
 * cards share in `WidgetCard.kt`: the `surfaceContainer` card at a 24 dp
 * corner, the 16 dp gutter, the 26 px code on the base line, the figure pills.
 * A challenge is a route, so its card has geography and draws it; an airframe
 * is an envelope, so the figures carry the content — range, and the runway it
 * needs — and there is **no map**. Four layouts, by width and by height
 * independently:
 *
 * - **Short compact** (the default, 2 × 1): two lines — the status dot and the
 *   airframe along the top with the date, the type code and the bare range on
 *   the base line.
 * - **Short wide** (4 × 1): the status in words moves up beside the date, and
 *   the runway figure joins a labelled range.
 * - **Tall** (two rows): the airframe name wraps into the band the challenge
 *   card fills with its map; the code and the status share the base line and
 *   the figures close the card, centred.
 *
 * The card is drawn at the reference launcher's real grants (One UI, see the
 * brand book's *Layout*): 176 × 90 dp at 2 × 1, 376 × 90 at 4 × 1, 184 × 204 at
 * 2 × 2, 376 × 204 at 4 × 2 — not at the `SizeMode.Responsive` bucket
 * breakpoints (140 / 250 wide, 40 / 150 tall), which are floors, not what
 * renders. The flown pill's quiet state is a filled `surfaceContainerHigh`
 * chip rather than the mock's outlined one, as in the app: Glance cannot draw
 * a themed stroke, and the mirror reproduces what ships.
 *
 * ```tsx
 * <AircraftWidgetCard
 *   state={{
 *     status: 'ready',
 *     date: '16 Sep',
 *     name: 'Boeing 787-9',
 *     typeCode: 'B789',
 *     flown: true,
 *     rangeText: '7,355 NM',
 *     runwayText: '9,800 ft',
 *   }}
 * />
 * ```
 */
export function AircraftWidgetCard({ state, wide = false, tall = false, className }: AircraftWidgetCardProps) {
  const classes = [
    'fp-widget-card',
    'fp-aircraft-widget',
    wide ? 'fp-aircraft-widget--wide' : 'fp-aircraft-widget--compact',
    tall ? 'fp-aircraft-widget--tall' : 'fp-aircraft-widget--short',
    className,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={classes}>
      <div className="fp-widget-card__content fp-aircraft-widget__content">
        {state.status === 'ready' ? (
          <>
            <div className="fp-widget-card__row fp-aircraft-widget__top">
              {!tall && !wide && <FlownDot flown={state.flown} />}
              <span className="fp-aircraft-widget__name">{state.name}</span>
              {!tall && wide && <FlownBadge flown={state.flown} />}
              <span className="fp-widget-card__date">{state.date}</span>
            </div>
            <div className="fp-widget-card__spacer" />
            {tall ? (
              <>
                <div className="fp-widget-card__row">
                  <span className="fp-widget-card__code">{state.typeCode}</span>
                  {wide ? <FlownBadge flown={state.flown} /> : <FlownDot flown={state.flown} />}
                </div>
                <div className="fp-widget-card__figures fp-aircraft-widget__figures--centred">
                  <Figures state={state} wide={wide} labelRange />
                </div>
              </>
            ) : (
              <div className="fp-widget-card__row">
                <span className="fp-widget-card__code">{state.typeCode}</span>
                <div className="fp-aircraft-widget__figures">
                  <Figures state={state} wide={wide} labelRange={wide} />
                </div>
              </div>
            )}
          </>
        ) : (
          <div className="fp-widget-card__message">
            {state.status === 'fleetEmpty'
              ? 'Add an aircraft to your fleet and one appears here each day.'
              : "Open the app to load today's aircraft."}
          </div>
        )}
      </div>
    </div>
  )
}

/**
 * The figure chips: the range, and on the wide card the runway too. The
 * runway is the figure that goes when there is no room for it, and the one
 * that is absent when the airframe names no takeoff distance. `labelRange`
 * is false only beside the short compact card's type code, where there is
 * room for the range or its label, not both; the unit says which figure it is.
 */
function Figures({ state, wide, labelRange }: { state: AircraftWidgetReady; wide: boolean; labelRange: boolean }) {
  return (
    <>
      <Figure label={labelRange ? 'RNG' : null} value={state.rangeText} />
      {wide && state.runwayText !== null && <Figure label="RWY" value={state.runwayText} />}
    </>
  )
}

/** One figure on its own quiet pill — the same `Figure` both widgets share in `WidgetCard.kt`. */
function Figure({ label, value }: { label: string | null; value: string }) {
  return (
    <div className="fp-widget-figure">
      {label !== null && <span className="fp-widget-figure__label">{label}</span>}
      <span className="fp-widget-figure__value">{value}</span>
    </div>
  )
}

/** The flown status in words. Flown is the accented state; not-flown recedes onto the figure chips' own surface. */
function FlownBadge({ flown }: { flown: boolean }) {
  return (
    <span className={['fp-aircraft-widget__badge', flown && 'fp-aircraft-widget__badge--flown'].filter(Boolean).join(' ')}>
      {flown ? 'FLOWN' : 'NOT FLOWN'}
    </span>
  )
}

/** The flown status as a mark, where the words do not fit. Labelled, as the real `Image` is, so it is not colour alone. */
function FlownDot({ flown }: { flown: boolean }) {
  return (
    <span
      role="img"
      aria-label={flown ? 'Flown' : 'Not flown'}
      className={['fp-aircraft-widget__dot', flown && 'fp-aircraft-widget__dot--flown'].filter(Boolean).join(' ')}
    />
  )
}
