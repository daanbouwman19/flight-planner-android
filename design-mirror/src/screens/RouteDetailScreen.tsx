import { PhoneFrame, TopAppBar } from '../components/AppChrome'
import { FlightRulesBadge, type FlightRules } from '../components/FlightRulesBadge'
import { RouteMap } from '../components/RouteMap'
import { SkyProfileHeight, type CelestialState } from '../components/SkyProfile'
import { MetarPanel, type MetarFigure } from '../components/MetarPanel'
import { ValueChip } from '../components/ValueChip'
import type { SkyCover } from '../geo/skyProfile'

export interface RouteDetailEnd {
  icao: string
  name: string
  lat: number
  lon: number
  runway: string
  rules?: FlightRules
  skyCover?: SkyCover
  ceilingFt?: number | null
  celestial?: CelestialState | null
  fogOrMist?: boolean
  visibilityStatuteMiles?: number | null
  /** The report's figures, already formatted and already in the reader's units. */
  figures?: MetarFigure[]
  /** The sky in words — what the cross-section draws, said plainly. */
  skyLine?: string
  /** `Observed 1425Z · 12 min ago`. */
  observed?: string
  /** Draws the age in the error colour, for a report past its currency. */
  stale?: boolean
  /** The raw report, behind a tap. */
  raw?: string
}

export interface RouteDetailPaneProps {
  aircraft: string
  departure: RouteDetailEnd
  destination: RouteDetailEnd
  /** Already formatted — `3,153 NM`. */
  distance: string
  /** Already formatted — `7:04`. */
  flightTime: string
  /** Already formatted — `271°`. */
  bearing?: string
  className?: string
}

/**
 * One route's detail, without a frame around it.
 *
 * On a phone this is the body of {@link RouteDetailScreen}; on a tablet it is the
 * trailing half of Plan's two-pane layout. It is one component in both places
 * because in the app the card's face travels here as a shared element — two
 * components that merely looked alike would come apart mid-transition.
 *
 * The airport **names** appear here, where there is room to read them. They are
 * deliberately absent from the route card, where every one of them truncated.
 */
export function RouteDetailPane({
  aircraft,
  departure,
  destination,
  distance,
  flightTime,
  bearing,
  className,
}: RouteDetailPaneProps) {
  return (
    <div className={['fp-screen', 'fp-content-cap', 'fp-content-cap--wide', className].filter(Boolean).join(' ')}>
      <div className="fp-screen__list">
        <div className="fp-detail-hero">
          <RouteMap
            depLat={departure.lat}
            depLon={departure.lon}
            destLat={destination.lat}
            destLon={destination.lon}
            aspect={360 / 200}
          />
        </div>

        <div className="fp-detail-codes">
          <div className="fp-detail-code">
            {departure.rules != null && <FlightRulesBadge rules={departure.rules} />}
            <span className="fp-detail-code__icao fp-type-headline-medium">{departure.icao}</span>
            <span className="fp-detail-code__name fp-type-label-small">{departure.name}</span>
          </div>
          <div className="fp-detail-code fp-detail-code--end">
            {destination.rules != null && <FlightRulesBadge rules={destination.rules} />}
            <span className="fp-detail-code__icao fp-type-headline-medium">{destination.icao}</span>
            <span className="fp-detail-code__name fp-type-label-small">{destination.name}</span>
          </div>
        </div>

        <div className="fp-detail-facts">
          <ValueChip label="DIST" value={distance} />
          <ValueChip label="TIME" value={flightTime} />
          {bearing != null && <ValueChip label="BRG" value={bearing} />}
          <ValueChip label="ACFT" value={aircraft} />
        </div>

        {/*
            One panel per end, in flight order. They are the same component the
            airport screen uses, so a station read here and a station read there
            are the same reading rather than two arrangements of the same facts.
          */}
        {departure.skyCover != null && (
          <MetarPanel {...endWeather(departure)} sceneHeight={SkyProfileHeight.RouteDetail} />
        )}

        {destination.skyCover != null && (
          <MetarPanel {...endWeather(destination)} sceneHeight={SkyProfileHeight.RouteDetail} />
        )}
      </div>
    </div>
  )
}

export interface RouteDetailScreenProps extends RouteDetailPaneProps {}

/**
 * One route in full, as its own screen.
 *
 * This is the phone form: a detail screen is not a section, so it takes the whole
 * window and the navigation is suppressed while it is up. On a tablet the same
 * content appears as {@link RouteDetailPane} beside Plan's list instead.
 */
export function RouteDetailScreen({ className, ...pane }: RouteDetailScreenProps) {
  return (
    <PhoneFrame className={className}>
      <TopAppBar title={`${pane.departure.icao} → ${pane.destination.icao}`} onBack={() => {}} />
      <RouteDetailPane {...pane} />
    </PhoneFrame>
  )
}

/**
 * The weather half of a {@link RouteDetailEnd}, as {@link MetarPanel}'s props.
 *
 * Split out so the two ends cannot pick up different sets of fields, which is
 * exactly what happened while each end had its own block of markup.
 */
function endWeather(end: RouteDetailEnd) {
  return {
    icao: end.icao,
    flightRules: end.rules,
    skyCover: end.skyCover,
    ceilingFt: end.ceilingFt,
    fogOrMist: end.fogOrMist,
    visibilityStatuteMiles: end.visibilityStatuteMiles,
    celestial: end.celestial,
    figures: end.figures,
    skyLine: end.skyLine,
    observed: end.observed,
    stale: end.stale,
    raw: end.raw,
  }
}
