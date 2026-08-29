import { NavigationBar, PhoneFrame } from '../components/AppChrome'
import { ModeSelector } from '../components/ModeSelector'

export interface Aircraft {
  /** `Cessna 172S Skyhawk`. */
  variant: string
  /** `Single Engine Piston`. */
  category: string
  /** Already formatted — `640 NM`. */
  range: string
  /** Already formatted — `1,685 ft`. */
  requiredRunway: string
  /** How many logged flights this airframe has. */
  flights?: number
}

export interface FleetScreenProps {
  aircraft: Aircraft[]
  /** Index into the category filter chips. */
  selectedCategory?: number
  categories?: string[]
  className?: string
}

/**
 * Fleet: the airframes a route can be generated for.
 *
 * Each row states the **envelope** — range and required runway — because those two
 * numbers are what actually constrain a generated route, and a fleet list that
 * only named aircraft would leave the reader unable to predict what Plan will do.
 */
export function FleetScreen({
  aircraft,
  selectedCategory = 0,
  categories = ['All', 'Piston', 'Turboprop', 'Jet'],
  className,
}: FleetScreenProps) {
  return (
    <PhoneFrame className={className} bottomBar={<NavigationBar selected="fleet" />}>
      <div className="fp-screen">
        <div className="fp-screen__header">
          <h1 className="fp-screen__title fp-type-headline-medium">Fleet</h1>
        </div>

        <div className="fp-screen__controls">
          <ModeSelector
            options={categories.map((label) => ({ label }))}
            selectedIndex={selectedCategory}
          />
        </div>

        <div className="fp-screen__list">
          {aircraft.map((a) => (
            <button type="button" className="fp-screen__row" key={a.variant}>
              <div className="fp-screen__row-main">
                <span className="fp-screen__row-title fp-type-title-medium">{a.variant}</span>
                <span className="fp-screen__row-detail fp-type-label-small">
                  {a.category} · {a.range} · {a.requiredRunway}
                </span>
              </div>
              {a.flights != null && (
                <span className="fp-screen__row-figure fp-type-label-large">{a.flights}</span>
              )}
            </button>
          ))}
        </div>
      </div>
    </PhoneFrame>
  )
}
