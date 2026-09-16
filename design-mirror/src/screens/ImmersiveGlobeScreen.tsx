import { PhoneFrame } from '../components/AppChrome'
import { GlobeRouteScene, type GlobeHeroEnd } from '../components/GlobeHero'
import { GlobeCameraControls, GlobeAttribution } from '../components/GlobeChrome'
import { ValueChip } from '../components/ValueChip'

export interface ImmersiveGlobeScreenProps {
  departure: GlobeHeroEnd
  destination: GlobeHeroEnd
  /** Already formatted — `B777-300ER`. */
  aircraft: string
  /** Already formatted — `3,153 NM`. */
  distance: string
  /** Already formatted — `291°`. */
  bearing?: string
  /** Already formatted — `7:04`. */
  flightTime?: string
  onCollapse?: () => void
  className?: string
}

/** How opaque the chips on the plate are — `ChipAlpha` in the app. */
const CHIP_ALPHA = 0.5

/**
 * The globe with the window to itself — the screen the hero's fullscreen action
 * pushes into. Ported from `ImmersiveGlobeScreen`.
 *
 * ### Why the figures come back here
 *
 * The route detail's spine says everything a chip would, in the right place,
 * which is why {@link GlobeHero} carries none. Here there is no spine and no
 * page: the whole window is a sphere, and without one line of figures on it the
 * screen would be a picture of a planet with no idea which flight it is about.
 * So the route reduces to one plate — the pair, the airframe, and the three
 * figures the arc is about. Same rule as the hero (*the figures the arc is about
 * sit next to the arc*), different answer because the arc is somewhere else.
 *
 * In the app the camera keeps its state across the transition, so the frame
 * grows while the globe holds still. The mirror is a still globe, so both ends
 * just show the same framing.
 */
export function ImmersiveGlobeScreen({
  departure,
  destination,
  aircraft,
  distance,
  bearing,
  flightTime,
  onCollapse,
  className,
}: ImmersiveGlobeScreenProps) {
  return (
    <PhoneFrame className={className}>
      <div className="fp-immersive-globe">
        <GlobeRouteScene
          departure={departure}
          destination={destination}
          aspect={360 / 800}
          topChromeFraction={0.11}
          className="fp-immersive-globe__sphere"
          chrome={
            <>
              <button
                type="button"
                className="fp-globe-control-button fp-immersive-globe__collapse"
                onClick={onCollapse}
                aria-label="Exit fullscreen"
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"
                    fill="currentColor"
                  />
                </svg>
              </button>

              <div className="fp-immersive-globe__controls">
                <GlobeCameraControls />
              </div>

              <div className="fp-immersive-globe__foot">
                <GlobeAttribution />
                <div className="fp-immersive-globe__plate">
                  <div className="fp-immersive-globe__plate-head">
                    <span className="fp-immersive-globe__route fp-type-title-large">
                      {departure.icao} → {destination.icao}
                    </span>
                    <span className="fp-immersive-globe__acft fp-type-label-large">{aircraft}</span>
                  </div>
                  <div className="fp-immersive-globe__chips">
                    <ValueChip label="DIST" value={distance} containerAlpha={CHIP_ALPHA} />
                    {bearing != null && (
                      <ValueChip label="BRG" value={bearing} containerAlpha={CHIP_ALPHA} />
                    )}
                    {flightTime != null && (
                      <ValueChip label="TIME" value={flightTime} containerAlpha={CHIP_ALPHA} />
                    )}
                  </div>
                </div>
              </div>
            </>
          }
        />
      </div>
    </PhoneFrame>
  )
}
