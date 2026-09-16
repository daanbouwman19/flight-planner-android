import { ChallengeWidgetCard } from '@flightplanner/design-mirror'

// The same day, route and figures as ChallengeWidget.kt's own PREVIEW_STATE —
// the picker's preview and this mirror's preview are the same plausible day.

/** The wide, four-cell layout: the full card with the airframe line. */
export const Wide = () => (
  <ChallengeWidgetCard
    wide
    state={{
      status: 'ready',
      date: 'Wed 16 Sep',
      departureIcao: 'EHAM',
      destinationIcao: 'KJFK',
      depLat: 52.3086,
      depLon: 4.7639,
      destLat: 40.6398,
      destLon: -73.7789,
      aircraftName: 'Boeing 787-9',
      distanceText: '3,162 NM',
      eteText: '6:33',
    }}
  />
)

/** Below the 220 dp threshold: the airframe line goes, codes and figures stay. */
export const Compact = () => (
  <ChallengeWidgetCard
    wide={false}
    state={{
      status: 'ready',
      date: 'Wed 16 Sep',
      departureIcao: 'EHAM',
      destinationIcao: 'KJFK',
      depLat: 52.3086,
      depLon: 4.7639,
      destLat: 40.6398,
      destLon: -73.7789,
      aircraftName: 'Boeing 787-9',
      distanceText: '3,162 NM',
      eteText: '6:33',
    }}
  />
)

/** A short domestic hop — the everyday case, not the long-haul preview. */
export const ShortHop = () => (
  <ChallengeWidgetCard
    wide
    state={{
      status: 'ready',
      date: 'Mon 21 Sep',
      departureIcao: 'EHAM',
      destinationIcao: 'EBBR',
      depLat: 52.3086,
      depLon: 4.7639,
      destLat: 50.9014,
      destLon: 4.4844,
      aircraftName: 'Cessna 172S Skyhawk',
      distanceText: '92 NM',
      eteText: '0:52',
    }}
  />
)

/** No airframes in the fleet yet, even after the auto-seed. */
export const FleetEmpty = () => (
  <ChallengeWidgetCard wide state={{ status: 'fleetEmpty' }} />
)

/** The index or the fleet could not be read. */
export const Unavailable = () => (
  <ChallengeWidgetCard wide state={{ status: 'unavailable' }} />
)
