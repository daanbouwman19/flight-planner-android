import { PhoneFrame, TopAppBar } from '../components/AppChrome'
import type { FlightRules } from '../components/FlightRulesBadge'
import { RunwayDiagram, type DiagramWind } from '../components/RunwayDiagram'
import { SkyProfileHeight, type CelestialState } from '../components/SkyProfile'
import { MetarPanel, type MetarFigure } from '../components/MetarPanel'
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
  /** The raw report. Revealed by {@link rawExpanded}. */
  metar?: string
  /** Whether the raw report is showing. Defaults to `false`. */
  rawExpanded?: boolean
  /**
   * Shown in place of the figures when there is no report for this station.
   *
   * The cross-section above still hatches rather than drawing a clear sky: an
   * unreported sky and a clear sky are different facts and must not look alike.
   */
  unavailableText?: string
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
  fogOrMist,
  visibilityStatuteMiles,
  figures,
  skyLine,
  observed,
  stale,
  metar,
  rawExpanded = false,
  unavailableText,
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

          {/*
              The whole weather block is one panel, the same one the route screen
              shows twice. The category badge lives inside it, beside the station,
              rather than in a card of its own: the category is a *reading* of this
              report, and separating the two invites them to disagree.
            */}
          <MetarPanel
            icao={icao}
            flightRules={rules}
            skyCover={skyCover}
            ceilingFt={ceilingFt}
            fogOrMist={fogOrMist}
            visibilityStatuteMiles={visibilityStatuteMiles}
            celestial={celestial}
            figures={figures}
            skyLine={skyLine}
            observed={observed}
            stale={stale}
            raw={metar}
            expanded={rawExpanded}
            unavailableText={unavailableText}
            sceneHeight={SkyProfileHeight.AirportDetail}
          />

          <div className="fp-detail-facts">
            <ValueChip label="ELEV" value={elevation} />
          </div>

          <div className="fp-screen__card">
            <span className="fp-screen__card-title fp-type-label-large">Runways</span>
            <div style={{ display: 'flex', justifyContent: 'center' }}>
              <RunwayDiagram runways={runways} wind={wind} size={260} />
            </div>
          </div>

        </div>
      </div>
    </PhoneFrame>
  )
}
