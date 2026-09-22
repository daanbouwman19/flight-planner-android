import { FlightPlannerTheme, WatchRouteFace } from '@flightplanner/design-mirror'

// Real routes. KCSM → LTAT is the route the SM-L350 showed on 2026-09-22, and
// OIKY → OSKL the one the phone showed beside it; coordinates are from the
// bundled airports.db, distances and times as GreatCircle computes them from the
// seed fleet's cruise speeds.
const longHaul = {
  status: 'ready' as const,
  departureIcao: 'KCSM',
  destinationIcao: 'LTAT',
  depLat: 35.3398,
  depLon: -99.2005,
  destLat: 38.4353,
  destLon: 38.091,
  distanceText: '5783 NM',
  eteText: '12:00',
  aircraftName: 'Boeing 777-200ER',
}

const regional = {
  status: 'ready' as const,
  departureIcao: 'OIKY',
  destinationIcao: 'OSKL',
  depLat: 29.5509,
  depLon: 55.6727,
  destLat: 37.0206,
  destLon: 41.1914,
  distanceText: '853 NM',
  eteText: '1:52',
  aircraftName: 'Antonov An225',
}

/** One route filling the face: the codes across the top, the figures and the airframe across the bottom, the great circle behind. */
export const Route = () => <WatchRouteFace state={longHaul} />

/**
 * The same face in the four looks the phone can publish. The two dark ones go
 * to true black rather than the phone's near-black — the display is OLED — and
 * Chart stays paper.
 */
export const Themes = () => (
  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, max-content)', gap: 16 }}>
    {(['brandLight', 'brandDark', 'cockpit', 'chart'] as const).map((theme) => (
      <FlightPlannerTheme key={theme} theme={theme}>
        <WatchRouteFace state={regional} />
      </FlightPlannerTheme>
    ))}
  </div>
)

/**
 * Wide codes and a long airframe name. The codes step down from 30 sp rather
 * than wrap, and the name wraps inside an inset narrower than the plates',
 * because the circle is narrower there. In Roboto `MMMX` is still wider than
 * its half of the row at the 22 sp floor, so its last letter is clipped — the
 * app's `maxLines = 1` clips the same way. See NOTES, *A finding about the watch*.
 */
export const LongNames = () => (
  <WatchRouteFace
    state={{
      status: 'ready',
      departureIcao: 'EDDM',
      destinationIcao: 'MMMX',
      depLat: 48.3538,
      depLon: 11.7861,
      destLat: 19.4358,
      destLon: -99.0703,
      distanceText: '5316 NM',
      eteText: '15:25',
      aircraftName: 'McDonnell Douglas MD-11 GE',
    }}
  />
)

/** A short hop: the frame never shows less than 25° of longitude, so the leg sits small in a readable stretch of coast. */
export const ShortHop = () => (
  <WatchRouteFace
    state={{
      status: 'ready',
      departureIcao: 'EHAM',
      destinationIcao: 'EHGG',
      depLat: 52.3086,
      depLon: 4.7639,
      destLat: 53.1191,
      destLon: 6.5777,
      distanceText: '82 NM',
      eteText: '0:40',
      aircraftName: 'Cessna 172',
    }}
  />
)

/** After a tap, once the phone has the route: a centred flash, the widest line on the circle. Gone after two seconds. */
export const HandoffSent = () => <WatchRouteFace state={longHaul} handoff="sent" />

/** The phone could not be reached. The same flash, not a dialog — the wearer carries on swiping. */
export const HandoffFailed = () => <WatchRouteFace state={longHaul} handoff="failed" />

/** The first batch is still being generated from the watch's own airport index. */
export const Loading = () => <WatchRouteFace state={{ status: 'loading' }} />

/** The watch's airport index could not be read. */
export const Unavailable = () => <WatchRouteFace state={{ status: 'unavailable' }} />
