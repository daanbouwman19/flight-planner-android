import { NavigationBar, PhoneFrame, RouteCard } from '@flightplanner/design-mirror'

/**
 * The frame every screen is drawn in, and the invariant it encodes.
 *
 * **The system bars are empty.** The window is edge to edge and both bars are
 * transparent, so content scrolls up under the clock and down under the gesture
 * handle. A scrim, however subtle, reads on a device as an opaque bar the moment a
 * card is behind it — both a scrim and an inset-padded container were built and
 * removed for exactly that reason. A concept that paints a bar here is a concept
 * that cannot be built.
 */
export const WithContentUnderTheBars = () => (
  <PhoneFrame bottomBar={<NavigationBar selected="plan" />}>
    <div style={{ padding: '36px 16px 16px', display: 'flex', flexDirection: 'column', gap: 12 }}>
      <RouteCard
        aircraft="Cessna 172S Skyhawk"
        category="Single Engine Piston"
        departure={{ icao: 'EHAM', lat: 52.3086, lon: 4.76389, runway: '12,467 ft', rules: 'VFR' }}
        destination={{ icao: 'EBBR', lat: 50.9014, lon: 4.48444, runway: '11,936 ft', rules: 'VFR' }}
        distance="92 NM"
        flightTime="0:52"
      />
      <RouteCard
        aircraft="Cessna 172S Skyhawk"
        category="Single Engine Piston"
        departure={{ icao: 'EHRD', lat: 51.9569, lon: 4.43722, runway: '7,218 ft', rules: 'MVFR' }}
        destination={{ icao: 'EGLL', lat: 51.4707, lon: -0.459909, runway: '12,799 ft', rules: 'IFR' }}
        distance="187 NM"
        flightTime="1:46"
      />
    </div>
  </PhoneFrame>
)

/** Empty, so the frame itself is legible. */
export const Bare = () => <PhoneFrame time="14:22" />
