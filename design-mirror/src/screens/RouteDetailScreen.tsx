import { PhoneFrame, TopAppBar } from '../components/AppChrome'
import { FlightRulesBadge, type FlightRules } from '../components/FlightRulesBadge'
import { RouteMap } from '../components/RouteMap'
import { SkyProfile, SkyProfileHeight, type CelestialState } from '../components/SkyProfile'
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
}

export interface RouteDetailScreenProps {
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
 * One route in full: the map as a hero, both ends, and the weather at each.
 *
 * The hero is the same map the card draws — in the app the card's face travels
 * here as a shared element, which is why the two must be the same component rather
 * than two that merely look alike.
 *
 * The airport **names** appear here, where there is room to read them. They are
 * deliberately absent from the card, where every one of them truncated.
 */
export function RouteDetailScreen({
  aircraft,
  departure,
  destination,
  distance,
  flightTime,
  bearing,
  className,
}: RouteDetailScreenProps) {
  return (
    <PhoneFrame className={className}>
      <TopAppBar title={`${departure.icao} → ${destination.icao}`} onBack={() => {}} />
      <div className="fp-screen">
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
              <span className="fp-detail-code__icao fp-type-headline-medium">
                {destination.icao}
              </span>
              <span className="fp-detail-code__name fp-type-label-small">{destination.name}</span>
            </div>
          </div>

          <div className="fp-detail-facts">
            <ValueChip label="DIST" value={distance} />
            <ValueChip label="TIME" value={flightTime} />
            {bearing != null && <ValueChip label="BRG" value={bearing} />}
            <ValueChip label="ACFT" value={aircraft} />
          </div>

          {departure.skyCover != null && (
            <div className="fp-screen__card">
              <span className="fp-screen__card-title fp-type-label-large">
                {departure.icao} · departure
              </span>
              <SkyProfile
                skyCover={departure.skyCover}
                ceilingFt={departure.ceilingFt}
                celestial={departure.celestial}
                height={SkyProfileHeight.RouteDetail}
              />
            </div>
          )}

          {destination.skyCover != null && (
            <div className="fp-screen__card">
              <span className="fp-screen__card-title fp-type-label-large">
                {destination.icao} · destination
              </span>
              <SkyProfile
                skyCover={destination.skyCover}
                ceilingFt={destination.ceilingFt}
                celestial={destination.celestial}
                height={SkyProfileHeight.RouteDetail}
              />
            </div>
          )}
        </div>
      </div>
    </PhoneFrame>
  )
}
