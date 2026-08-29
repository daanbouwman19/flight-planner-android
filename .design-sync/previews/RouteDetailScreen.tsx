import { RouteDetailScreen } from '@flightplanner/design-mirror'

/**
 * A trans-Atlantic leg with weather at both ends.
 *
 * The hero is the same map component the route card draws — in the app the card's
 * face travels here as a shared element, which is why the two must be one component
 * rather than two that merely look alike.
 */
export const TransAtlantic = () => (
  <RouteDetailScreen
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
)

/** A short European hop where both ends are fine. */
export const ShortHop = () => (
  <RouteDetailScreen
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
      skyCover: { kind: 'layers', layers: [{ cover: 'FEW', baseFt: 4200 }] },
      ceilingFt: null,
      celestial: { sunElevationDeg: 44, sunAzimuthDeg: 180 },
      visibilityStatuteMiles: 10,
      figures: [
        { label: 'WIND', value: '200° 8 kt' },
        { label: 'VIS', value: '10+ SM' },
        { label: 'CEIL', value: 'Unlimited' },
        { label: 'QNH', value: '1021 hPa' },
      ],
      skyLine: 'Few at 4,200 ft',
      observed: '1420Z · 5 min ago',
    }}
    destination={{
      icao: 'EBBR',
      name: 'Brussels Airport',
      lat: 50.9014,
      lon: 4.48444,
      runway: '11,936 ft',
      rules: 'VFR',
      skyCover: { kind: 'clear' },
      ceilingFt: null,
      celestial: { sunElevationDeg: 45, sunAzimuthDeg: 178 },
      visibilityStatuteMiles: 10,
      figures: [
        { label: 'WIND', value: '210° 6 kt' },
        { label: 'VIS', value: '10+ SM' },
        { label: 'CEIL', value: 'Unlimited' },
      ],
      skyLine: 'Clear below 12,000 ft',
      observed: '1420Z · 5 min ago',
    }}
  />
)
