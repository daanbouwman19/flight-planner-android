import { RouteDetailPane } from '@flightplanner/design-mirror'

/**
 * One route's detail without a frame — the trailing pane of Plan's wide layout.
 *
 * The same component is the body of `RouteDetailScreen` on a phone. It is one
 * component in both places because in the app the route card's face travels here
 * as a shared element; two components that merely looked alike would come apart
 * mid-transition.
 *
 * The airport **names** appear here, where there is room to read them — they are
 * deliberately absent from the card, where every one of them truncated.
 */
export const TransAtlantic = () => (
  <div style={{ width: 560, background: 'var(--fp-surface-container-low)' }}>
    <RouteDetailPane
      aircraft="B777-300ER"
      distance="3,153 NM"
      flightTime="7:04"
      bearing="291°"
      departure={{
        icao: 'EHAM',
        name: 'Amsterdam Airport Schiphol',
        lat: 52.3086,
        lon: 4.76389,
        runway: '12,467 ft',
        rules: 'VFR',
        skyCover: { kind: 'layers', layers: [{ cover: 'SCATTERED', baseFt: 2800 }] },
        ceilingFt: null,
        celestial: { sunElevationDeg: 31, sunAzimuthDeg: 200 },
        visibilityStatuteMiles: 10,
        figures: [
          { label: 'WIND', value: '250° 14 kt' },
          { label: 'VIS', value: '10+ SM' },
          { label: 'CEIL', value: 'Unlimited' },
          { label: 'QNH', value: '1016 hPa' },
        ],
        skyLine: 'Scattered at 2,800 ft',
        observed: '1355Z · 7 min ago',
      }}
      destination={{
        icao: 'KJFK',
        name: 'John F. Kennedy International Airport',
        lat: 40.6394,
        lon: -73.7793,
        runway: '14,511 ft',
        rules: 'IFR',
        skyCover: { kind: 'layers', layers: [{ cover: 'OVERCAST', baseFt: 800 }] },
        ceilingFt: 800,
        celestial: { sunElevationDeg: 12, sunAzimuthDeg: 110 },
        visibilityStatuteMiles: 4,
        figures: [
          { label: 'WIND', value: '090° 22G31 kt' },
          { label: 'VIS', value: '4 SM' },
          { label: 'CEIL', value: '800 ft' },
          { label: 'QNH', value: '29.74 inHg' },
        ],
        skyLine: 'Overcast at 800 ft',
        observed: '1351Z · 11 min ago',
      }}
    />
  </div>
)

/** A short hop with no weather loaded at either end. */
export const NoWeather = () => (
  <div style={{ width: 560, background: 'var(--fp-surface-container-low)' }}>
    <RouteDetailPane
      aircraft="C172S"
      distance="92 NM"
      flightTime="0:52"
      bearing="196°"
      departure={{
        icao: 'EHAM',
        name: 'Amsterdam Airport Schiphol',
        lat: 52.3086,
        lon: 4.76389,
        runway: '12,467 ft',
        rules: 'VFR',
      }}
      destination={{
        icao: 'EBBR',
        name: 'Brussels Airport',
        lat: 50.9014,
        lon: 4.48444,
        runway: '11,936 ft',
        rules: 'VFR',
      }}
    />
  </div>
)
