import { PhoneFrame, TopAppBar } from '../components/AppChrome'
import { FlightRulesBadge, type FlightRules } from '../components/FlightRulesBadge'
import { RunwayDiagram, type DiagramWind } from '../components/RunwayDiagram'
import { SkyProfile, type CelestialState } from '../components/SkyProfile'
import { ValueChip } from '../components/ValueChip'
import type { Runway } from '../geo/runwayLayout'
import type { SkyCover } from '../geo/skyProfile'

export interface AirportDetailScreenProps {
  icao: string
  name: string
  /** `Amsterdam, Netherlands`. */
  location: string
  /** Already formatted — `-11 ft`. */
  elevation: string
  rules?: FlightRules
  runways: Runway[]
  wind?: DiagramWind | null
  skyCover: SkyCover
  ceilingFt?: number | null
  celestial?: CelestialState | null
  /** The raw METAR, shown verbatim under the decoded panel. */
  metar?: string
  className?: string
}

/**
 * One airport: its weather, its runways, and what a pilot would do with both.
 *
 * The Sky Profile and the runway diagram sit next to each other on purpose. The
 * profile says what the air is doing; the diagram says which strip that points you
 * at. Between them the wind stops being a number to compare against a heading in
 * your head and becomes a picture.
 */
export function AirportDetailScreen({
  icao,
  name,
  location,
  elevation,
  rules,
  runways,
  wind,
  skyCover,
  ceilingFt,
  celestial,
  metar,
  className,
}: AirportDetailScreenProps) {
  return (
    <PhoneFrame className={className}>
      <TopAppBar title={icao} onBack={() => {}} />
      <div className="fp-screen fp-content-cap fp-content-cap--wide">
        <div className="fp-screen__list">
          <div>
            <div className="fp-screen__row-title fp-type-title-medium">{name}</div>
            <div className="fp-screen__row-detail fp-type-body-medium">
              {location} · {elevation}
            </div>
          </div>

          <div className="fp-detail-hero">
            <SkyProfile
              skyCover={skyCover}
              ceilingFt={ceilingFt}
              celestial={celestial}
            />
          </div>

          <div className="fp-detail-facts">
            {rules != null && (
              <div className="fp-screen__card" style={{ padding: 12, gap: 8 }}>
                <span className="fp-screen__card-title fp-type-label-small">CATEGORY</span>
                <FlightRulesBadge rules={rules} />
              </div>
            )}
            <ValueChip label="ELEV" value={elevation} />
          </div>

          <div className="fp-screen__card">
            <span className="fp-screen__card-title fp-type-label-large">Runways</span>
            <div style={{ display: 'flex', justifyContent: 'center' }}>
              <RunwayDiagram runways={runways} wind={wind} size={260} />
            </div>
          </div>

          {metar != null && (
            <div className="fp-screen__card">
              <span className="fp-screen__card-title fp-type-label-large">Report</span>
              {/* Verbatim, in a figure setting: a METAR is a chart figure from end
                  to end and must never be reordered by the bidi algorithm. */}
              <code className="fp-metar fp-type-body-small">{metar}</code>
            </div>
          )}
        </div>
      </div>
    </PhoneFrame>
  )
}
