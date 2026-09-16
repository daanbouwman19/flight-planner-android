import { RouteMap } from './RouteMap'

export interface ChallengeWidgetReady {
  status: 'ready'
  /** Already formatted for the reader's locale — `Wed 16 Sep`. */
  date: string
  departureIcao: string
  destinationIcao: string
  depLat: number
  depLon: number
  destLat: number
  destLon: number
  aircraftName: string
  /** Already formatted, in the unit the user chose — `3,162 NM`. */
  distanceText: string
  /** Already formatted — `6:33`. */
  eteText: string
}

export interface ChallengeWidgetFleetEmpty {
  status: 'fleetEmpty'
}

export interface ChallengeWidgetUnavailable {
  status: 'unavailable'
}

export type ChallengeWidgetState = ChallengeWidgetReady | ChallengeWidgetFleetEmpty | ChallengeWidgetUnavailable

export interface ChallengeWidgetCardProps {
  state: ChallengeWidgetState
  /**
   * The four-cell layout with the airframe name along the top. Below the real
   * widget's 220 dp threshold the compact two-cell layout drops it. Default
   * `true`.
   */
  wide?: boolean
  className?: string
}

/**
 * "Today's challenge" — the home-screen widget, as the route card's own
 * grammar taken out onto the launcher.
 *
 * Ported from `ChallengeWidget.kt`'s `ChallengeContent`. The real widget is
 * Glance (`RemoteViews`), not Compose — it can only show bitmaps, so its map
 * is three alpha masks rendered off-screen by the same `renderRouteMapLayers`
 * the app's `RouteMap` draws with, then tinted by the launcher at display
 * time. This mirror has no such constraint: `RouteMap` itself draws the same
 * geometry directly, with the same `topInset` (16 dp padding + the 20 dp
 * airframe line) that keeps the route off the card's top band. The card's
 * surface is `surfaceContainer`, the chip `surfaceContainerHigh` — the app's
 * own route-card tones, not the launcher's wallpaper accent, which is
 * deliberately not what a card that has to look like the app's own uses.
 *
 * ```tsx
 * <ChallengeWidgetCard
 *   state={{
 *     status: 'ready',
 *     date: 'Wed 16 Sep',
 *     departureIcao: 'EHAM',
 *     destinationIcao: 'KJFK',
 *     depLat: 52.3086, depLon: 4.7639,
 *     destLat: 40.6398, destLon: -73.7789,
 *     aircraftName: 'Boeing 787-9',
 *     distanceText: '3,162 NM',
 *     eteText: '6:33',
 *   }}
 * />
 * ```
 */
export function ChallengeWidgetCard({ state, wide = true, className }: ChallengeWidgetCardProps) {
  return (
    <div
      className={['fp-widget-card', wide ? 'fp-widget-card--wide' : 'fp-widget-card--compact', className]
        .filter(Boolean)
        .join(' ')}
    >
      {state.status === 'ready' && (
        <div className="fp-widget-card__map">
          <RouteMap
            depLat={state.depLat}
            depLon={state.depLon}
            destLat={state.destLat}
            destLon={state.destLon}
            // The real widget's two responsive cell sizes: 250 x 100 dp wide,
            // 140 x 100 dp compact (`ChallengeWidget.WIDE` / `.COMPACT`).
            aspect={wide ? 250 / 100 : 140 / 100}
            // 16 dp padding + the 20 dp airframe/date line — the same figure
            // `renderChallengeMap`'s `topInsetPx` computes.
            topInset={36}
          />
        </div>
      )}
      <div className="fp-widget-card__content">
        {state.status === 'ready' ? (
          <>
            <div className="fp-widget-card__row">
              {wide ? (
                <span className="fp-widget-card__aircraft">{state.aircraftName}</span>
              ) : (
                <span />
              )}
              <span className="fp-widget-card__date">{state.date}</span>
            </div>
            <div className="fp-widget-card__spacer" />
            <div className="fp-widget-card__row fp-widget-card__row--codes">
              <span className="fp-widget-card__code">{state.departureIcao}</span>
              <span className="fp-widget-card__code">{state.destinationIcao}</span>
            </div>
            <div className="fp-widget-card__figures">
              <ChallengeFigure label="DIST" value={state.distanceText} />
              <ChallengeFigure label="ETE" value={state.eteText} />
            </div>
          </>
        ) : (
          <div className="fp-widget-card__message">
            {state.status === 'fleetEmpty'
              ? 'Add an aircraft to your fleet and a challenge appears here.'
              : "Open the app to load today's challenge."}
          </div>
        )}
      </div>
    </div>
  )
}

/** One figure on its own quiet pill, so the coast can pass behind it. Not `ValueChip` — the real widget has its own, in Glance's vocabulary rather than Compose's. */
function ChallengeFigure({ label, value }: { label: string; value: string }) {
  return (
    <div className="fp-widget-figure">
      <span className="fp-widget-figure__label">{label}</span>
      <span className="fp-widget-figure__value">{value}</span>
    </div>
  )
}
