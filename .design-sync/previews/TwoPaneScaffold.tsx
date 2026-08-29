import { RouteCard, TwoPaneScaffold } from '@flightplanner/design-mirror'

const card = {
  aircraft: 'Cessna 172S Skyhawk',
  category: 'Single Engine Piston',
  departure: { icao: 'EHAM', lat: 52.3086, lon: 4.76389, runway: '12,467 ft', rules: 'VFR' as const },
  destination: { icao: 'EBBR', lat: 50.9014, lon: 4.48444, runway: '11,936 ft', rules: 'VFR' as const },
  distance: '92 NM',
  flightTime: '0:52',
}

/**
 * List leading, detail trailing — the wide form of Plan, Fleet and Logbook.
 *
 * On a phone these are two screens and a navigation between them; here they are
 * one screen, and selecting a row replaces the detail pane **in place**. That is
 * why a pane change is a fade-through and never a slide: a slide would claim a
 * direction the selection does not have.
 *
 * The detail sits on its own surface so the split is legible without a rule
 * between the panes, which would read as a border rather than as two surfaces.
 */
export const ListAndDetail = () => (
  <div style={{ height: 460, width: 1000, background: 'var(--fp-background)' }}>
    <TwoPaneScaffold
      list={
        <div className="fp-screen fp-content-cap">
          <div className="fp-screen__list" style={{ paddingTop: 16 }}>
            <RouteCard {...card} />
            <RouteCard {...card} />
          </div>
        </div>
      }
      detail={
        <div className="fp-screen">
          <div className="fp-screen__list" style={{ paddingTop: 16 }}>
            <div className="fp-screen__card">
              <span className="fp-screen__card-title fp-type-label-large">EHAM → EBBR</span>
              <span className="fp-type-body-medium">
                The selected route's detail, shown beside the list rather than on a
                screen of its own.
              </span>
            </div>
          </div>
        </div>
      }
    />
  </div>
)
