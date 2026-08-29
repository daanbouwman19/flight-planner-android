import {
  NavigationRail,
  RouteCard,
  TabletFrame,
  TwoPaneScaffold,
} from '@flightplanner/design-mirror'

const card = {
  aircraft: 'Cessna 172S Skyhawk',
  category: 'Single Engine Piston',
  departure: { icao: 'EHAM', lat: 52.3086, lon: 4.76389, runway: '12,467 ft', rules: 'VFR' as const },
  destination: { icao: 'EBBR', lat: 50.9014, lon: 4.48444, runway: '11,936 ft', rules: 'VFR' as const },
  distance: '92 NM',
  flightTime: '0:52',
}

/**
 * A 1280 × 800 tablet with the content width-capped.
 *
 * The same invariant as the phone holds and is easier to break at this size: the
 * system bars stay empty and content runs under the clock. What changes is the
 * navigation — a rail on the leading edge — and that the column is capped rather
 * than stretched, so the extra width becomes margin.
 */
export const WithRail = () => (
  <TabletFrame rail={<NavigationRail selected="plan" />}>
    <div className="fp-screen fp-content-cap" style={{ paddingTop: 44 }}>
      <div className="fp-screen__list">
        <RouteCard {...card} />
        <RouteCard {...card} />
      </div>
    </div>
  </TabletFrame>
)

/** The two-pane split: list leading, detail trailing on its own surface. */
export const TwoPane = () => (
  <TabletFrame rail={<NavigationRail selected="plan" />}>
    <TwoPaneScaffold
      list={
        <div className="fp-screen fp-content-cap" style={{ paddingTop: 44 }}>
          <div className="fp-screen__list">
            <RouteCard {...card} />
            <RouteCard {...card} />
          </div>
        </div>
      }
      detail={
        <div className="fp-screen" style={{ paddingTop: 44 }}>
          <div className="fp-screen__card">
            <span className="fp-screen__card-title fp-type-label-large">Detail pane</span>
            <span className="fp-type-body-medium">
              Selecting a row replaces this in place, so a pane change is a
              fade-through and never a slide.
            </span>
          </div>
        </div>
      }
    />
  </TabletFrame>
)
