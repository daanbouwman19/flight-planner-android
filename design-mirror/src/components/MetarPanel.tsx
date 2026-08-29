import { SkyProfile, SkyProfileHeight, type CelestialState } from './SkyProfile'
import type { SkyCover, SkyPhase } from '../geo/skyProfile'
import { FlightRulesBadge, type FlightRules } from './FlightRulesBadge'
import { ValueChip } from './ValueChip'

export interface MetarFigure {
  /** The short caption — `WIND`, `VIS`, `CEIL`, `QNH`, `TEMP`. */
  label: string
  /** Already formatted and already in the reader's units — `270° 35G50 kt`. */
  value: string
}

export interface MetarPanelProps {
  /** The station. `—` when there is none. */
  icao?: string
  /**
   * The flight category, or omitted when the report did not permit one.
   *
   * `unknown` draws no badge at all rather than a grey one: a category the app
   * could not determine is not a fifth category.
   */
  flightRules?: FlightRules
  /** What the station reported about the sky, for the cross-section. */
  skyCover?: SkyCover
  ceilingFt?: number | null
  fogOrMist?: boolean
  visibilityStatuteMiles?: number | null
  /** Resolved by the caller at the **observation instant**, never at now. */
  celestial?: CelestialState | null
  /**
   * The time of day, for a report that carries no position.
   *
   * Only consulted when {@link celestial} is absent — a report with a sun in it
   * says what time it is, and the scene follows that rather than this. Defaults to
   * `DAY`.
   */
  phase?: SkyPhase
  /**
   * The figures, in report order, each conditional on its own group.
   *
   * A METAR is a set of optional groups, so a station that sends no altimeter
   * shows four chips rather than a fifth reading `—`. The empty figure is for a
   * value that should exist and does not; a group never sent is not that.
   */
  figures?: MetarFigure[]
  /** The sky in words — what the scene above draws, said plainly. */
  skyLine?: string
  /** `Observed 1425Z · 12 min ago`. Already formatted. */
  observed?: string
  /**
   * Draws the age in the error colour, for a report past its currency.
   *
   * The staleness of a reading is a fact about the reading, so it is stated on
   * the reading rather than as a banner over the panel.
   */
  stale?: boolean
  /** The raw report, revealed on tap. */
  raw?: string
  /** Whether the raw text is showing. Defaults to `false`. */
  expanded?: boolean
  /** Shown in place of everything below the scene when there is no report. */
  unavailableText?: string
  /** One of {@link SkyProfileHeight}. Defaults to the route-detail height. */
  sceneHeight?: number
  className?: string
}

const EMPTY_FIGURE = '—'

/**
 * One station's weather: the sky drawn, then the sky in figures, then the sky in
 * words, with the raw report behind a tap.
 *
 * **The order is the argument.** The cross-section is edge to edge at the top so
 * the horizon meets the card's rounded corners — inset on all four sides it reads
 * as a picture of a diagram rather than as the diagram. Under it the station, its
 * category and the report's age share one line, because the age is three
 * characters wide and belongs beside the identifier it qualifies rather than in a
 * row of its own.
 *
 * The figures are {@link ValueChip}s — the same component the route card sets DIST
 * and ETE in. That is most of what makes this read as part of the same application
 * rather than as a weather widget dropped into it. They are chunked two to a row
 * rather than flowed, so the labels align down an edge; a short last row keeps its
 * hole rather than stretching, because a `QNH` chip twice the width of the `CEIL`
 * above it would read as more important.
 *
 * The raw text is collapsed by default and expands on a tap. Forty characters of
 * monospace under every panel — twice over on the route screen — pushed everything
 * read at a glance off the top of it.
 *
 * ```tsx
 * <MetarPanel
 *   icao="EHAM"
 *   flightRules="MVFR"
 *   skyCover={{ kind: 'layers', layers: [{ cover: 'BROKEN', baseFt: 1800 }] }}
 *   ceilingFt={1800}
 *   figures={[{ label: 'WIND', value: '240° 18 kt' }, { label: 'VIS', value: '6 SM' }]}
 *   skyLine="Broken at 1,800 ft"
 *   observed="Observed 1425Z · 12 min ago"
 * />
 * ```
 */
export function MetarPanel({
  icao,
  flightRules,
  skyCover = { kind: 'unknown' },
  ceilingFt,
  fogOrMist,
  visibilityStatuteMiles,
  celestial,
  phase,
  figures = [],
  skyLine,
  observed,
  stale = false,
  raw,
  expanded = false,
  unavailableText,
  sceneHeight = SkyProfileHeight.RouteDetail,
  className,
}: MetarPanelProps) {
  const reported = unavailableText == null

  // Two to a row, and the hole in a short last row is kept on purpose.
  const rows: MetarFigure[][] = []
  for (let i = 0; i < figures.length; i += 2) rows.push(figures.slice(i, i + 2))

  return (
    <section className={['fp-metar', className].filter(Boolean).join(' ')}>
      <SkyProfile
        skyCover={skyCover}
        ceilingFt={ceilingFt}
        fogOrMist={fogOrMist}
        visibilityStatuteMiles={visibilityStatuteMiles}
        celestial={celestial}
        phase={phase}
        height={sceneHeight}
      />

      <div className="fp-metar__body">
        <div className="fp-metar__station">
          <span className="fp-metar__icao fp-type-headline-small">{icao ?? EMPTY_FIGURE}</span>
          {flightRules != null && flightRules !== 'UNKNOWN' && (
            <FlightRulesBadge rules={flightRules} />
          )}
          {observed != null && (
            <span
              className={[
                'fp-metar__age',
                'fp-type-label-small',
                stale ? 'fp-metar__age--stale' : null,
              ]
                .filter(Boolean)
                .join(' ')}
            >
              {observed}
            </span>
          )}
        </div>

        {reported ? (
          <>
            {rows.length > 0 && (
              <div className="fp-metar__figures">
                {rows.map((row, i) => (
                  <div className="fp-metar__figure-row" key={i}>
                    {row.map((figure) => (
                      <ValueChip
                        key={figure.label}
                        label={figure.label}
                        value={figure.value}
                        className="fp-metar__chip"
                      />
                    ))}
                    {row.length === 1 && <span className="fp-metar__chip" />}
                  </div>
                ))}
              </div>
            )}

            {skyLine != null && (
              <span className="fp-metar__sky-line fp-type-body-medium">{skyLine}</span>
            )}

            {raw != null && expanded && (
              <div className="fp-metar__raw">
                <pre className="fp-metar__raw-text fp-type-body-small">{raw}</pre>
              </div>
            )}
          </>
        ) : (
          <span className="fp-metar__sky-line fp-type-body-medium">{unavailableText}</span>
        )}
      </div>
    </section>
  )
}
