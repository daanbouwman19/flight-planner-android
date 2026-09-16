import { GlobeRouteScene } from '@flightplanner/design-mirror'

const EHAM = { icao: 'EHAM', lat: 52.308601, lon: 4.76389 }
const KJFK = { icao: 'KJFK', lat: 40.639447, lon: -73.779317 }
const OMDB = { icao: 'OMDB', lat: 25.252778, lon: 55.364444 }

/**
 * The route on the sphere without any chrome — the arc, the two endpoint dots,
 * and the DEP/DEST plates, and nothing else. This is the shared base of
 * `GlobeHero` and `ImmersiveGlobeScreen`; reach for it when a concept wants the
 * globe to be one element of a larger composition and will place its own
 * controls.
 */
export const Bare = () => (
  <div style={{ width: 360, height: 320 }}>
    <GlobeRouteScene departure={EHAM} destination={KJFK} aspect={360 / 320} />
  </div>
)

/** A longer leg, framed wider. The plates still hang from their own dots. */
export const LongLeg = () => (
  <div style={{ width: 360, height: 320 }}>
    <GlobeRouteScene departure={EHAM} destination={OMDB} aspect={360 / 320} />
  </div>
)

/** With a caller's chrome slotted onto the glass. */
export const WithChrome = () => (
  <div style={{ width: 360, height: 320 }}>
    <GlobeRouteScene
      departure={EHAM}
      destination={KJFK}
      aspect={360 / 320}
      chrome={
        <span
          style={{
            position: 'absolute',
            left: 12,
            bottom: 12,
            padding: '4px 8px',
            borderRadius: 'var(--fp-shape-extra-small)',
            background: 'color-mix(in srgb, var(--fp-surface-container) 82%, transparent)',
            color: 'var(--fp-on-surface-variant)',
            font: '500 12px/16px Roboto, sans-serif',
          }}
        >
          3,153 NM
        </span>
      }
    />
  </div>
)
