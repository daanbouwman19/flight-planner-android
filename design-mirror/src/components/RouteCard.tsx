import { FlightRulesBadge, type FlightRules } from './FlightRulesBadge'
import { RouteMap } from './RouteMap'
import { ValueChip } from './ValueChip'

/** Translucent, so the coastline passes behind the figures rather than under a panel. */
const CHIP_CONTAINER_ALPHA = 0.7

export interface RouteEndpoint {
  /** The ICAO code. The airport's *name* is deliberately not shown on the card. */
  icao: string
  /** Degrees north. */
  lat: number
  /** Degrees east. */
  lon: number
  /** The longest runway, already formatted — `12,467 ft`. */
  runway: string
  /**
   * Draws the runway figure in the error colour.
   *
   * Colour carries the warning rather than a separate icon, so the figure and the
   * judgement of it are the same glyphs.
   */
  runwayTooShort?: boolean
  /** The station's flight category. Defaults to `UNKNOWN`, which still holds space. */
  rules?: FlightRules
}

export interface RouteCardProps {
  aircraft: string
  /** The aircraft's category — `Single Engine Piston`. */
  category: string
  departure: RouteEndpoint
  destination: RouteEndpoint
  /** Already formatted — `3,312 NM`. */
  distance: string
  /** Already formatted — `7:12`. */
  flightTime: string
  /** Minimum card height in px. Defaults to `180`; `132` in a short window. */
  minHeight?: number
  /**
   * The aspect ratio the background map is projected through.
   *
   * It should match the card's own shape. The map fills the card and does not
   * preserve its own ratio, so a projection computed for a different shape
   * squashes the land — at the default 360 × 180 card that is a 2:1 window.
   */
  mapAspect?: number
  onClick?: () => void
  className?: string
}

/**
 * One generated route: the app's most-seen surface.
 *
 * The map is the card's **background**, not an inset panel, and the figures are
 * printed straight onto it. That works because the map keeps land at 8 % and its
 * coast at 16 %, leaving the route the only saturated thing on the card — so no
 * scrim is needed, and the chips are translucent enough for the coastline to pass
 * behind them.
 *
 * **The two codes sit at the two edges, not on their endpoints.** Anchoring them
 * to the map looks right in a single mockup and collides unpredictably in a list,
 * because the endpoints land somewhere different on every card. At the edges they
 * are in the same two places on every row, which is what makes a column of cards
 * scannable — and the map already says which end is which, with a hollow ring for
 * the departure and a filled dot for the destination.
 *
 * **The airport names are deliberately absent.** They were here, and on a 360 dp
 * window every one of them truncated to "Stangland A...", which is worse than
 * absent. The name belongs to the detail screen, where there is room to read it.
 *
 * ```tsx
 * <RouteCard
 *   aircraft="Cessna 172S Skyhawk"
 *   category="Single Engine Piston"
 *   departure={{ icao: 'EHAM', lat: 52.31, lon: 4.76, runway: '12,467 ft', rules: 'VFR' }}
 *   destination={{ icao: 'EGLL', lat: 51.4706, lon: -0.4619, runway: '12,799 ft', rules: 'MVFR' }}
 *   distance="199 NM"
 *   flightTime="1:52"
 * />
 * ```
 */
export function RouteCard({
  aircraft,
  category,
  departure,
  destination,
  distance,
  flightTime,
  minHeight = 180,
  mapAspect = 2,
  onClick,
  className,
}: RouteCardProps) {
  const Root = onClick ? 'button' : 'div'
  return (
    <Root
      className={['fp-route-card', className].filter(Boolean).join(' ')}
      style={{ minHeight }}
      onClick={onClick}
      // One description for the whole card. Left to itself it announces eleven
      // separate nodes — two codes, two chips and three labelled figures — which
      // is technically complete and unusable.
      aria-label={
        `${aircraft}, ${departure.icao} to ${destination.icao}, ` +
        `${distance}, ${flightTime}`
      }
    >
      <div className="fp-route-card__map">
        <RouteMap
          depLat={departure.lat}
          depLon={departure.lon}
          destLat={destination.lat}
          destLon={destination.lon}
          aspect={mapAspect}
        />
      </div>

      <div className="fp-route-card__content">
        <div className="fp-route-card__aircraft">
          <span className="fp-route-card__aircraft-name fp-type-title-small">{aircraft}</span>
          <span className="fp-route-card__aircraft-category fp-type-label-small">{category}</span>
        </div>

        {/* The gap between the eyebrow and the codes is the map's, and it takes
            whatever height the card has left over. */}
        <div className="fp-route-card__gap" />

        <div className="fp-route-card__ends">
          <AirportEnd end={departure} align="start" />
          <AirportEnd end={destination} align="end" />
        </div>

        {/* Two equal columns across the full width. An earlier version left a
            third of the row empty so the map ran out from under the chips; on a
            360 dp phone that left 122 dp a chip and "DIST 2,847 NM" wrapped. */}
        <div className="fp-route-card__facts">
          <ValueChip label="DIST" value={distance} containerAlpha={CHIP_CONTAINER_ALPHA} />
          <ValueChip label="TIME" value={flightTime} containerAlpha={CHIP_CONTAINER_ALPHA} />
        </div>
      </div>
    </Root>
  )
}

function AirportEnd({ end, align }: { end: RouteEndpoint; align: 'start' | 'end' }) {
  return (
    <div className={`fp-route-card__end fp-route-card__end--${align}`}>
      {/* The slot holds its space even when the category is unknown, so a card
          whose weather has not arrived does not relayout when it does. */}
      <FlightRulesBadge rules={end.rules ?? 'UNKNOWN'} />
      <span className="fp-route-card__icao fp-type-headline-small">{end.icao}</span>
      <span
        className={[
          'fp-route-card__runway',
          'fp-type-label-small',
          end.runwayTooShort ? 'fp-route-card__runway--short' : null,
        ]
          .filter(Boolean)
          .join(' ')}
        aria-label={end.runwayTooShort ? 'Runway shorter than this aircraft needs' : undefined}
      >
        RWY {end.runway}
      </span>
    </div>
  )
}
