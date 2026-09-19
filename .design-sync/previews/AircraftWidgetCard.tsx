import { AircraftWidgetCard } from '@flightplanner/design-mirror'

// The same day and airframe as AircraftWidget.kt's own PREVIEW_STATE — the
// picker's preview and this mirror's preview are the same plausible day.
const boeing = {
  status: 'ready' as const,
  date: '16 Sep',
  name: 'Boeing 787-9',
  typeCode: 'B789',
  flown: true,
  rangeText: '7,355 NM',
  runwayText: '9,800 ft',
}

/** The default, two cells by one: the status dot, the airframe and the date, the type code and the bare range. */
export const Compact = () => <AircraftWidgetCard state={boeing} />

/** Four cells by one: the status in words beside the date, the runway joining a labelled range. */
export const Wide = () => <AircraftWidgetCard wide state={boeing} />

/** Two by two: the challenge card's three bands, the name wrapping into the band that card fills with its map. */
export const CompactTall = () => <AircraftWidgetCard tall state={boeing} />

/** Four by two: both figures, centred, and the status in words on the code line. */
export const WideTall = () => <AircraftWidgetCard wide tall state={boeing} />

/** Not yet flown, on the compact card — the quiet dot — and a name the top line has to ellipsise. */
export const NotFlown = () => (
  <AircraftWidgetCard
    state={{
      status: 'ready',
      date: '21 Sep',
      name: 'Cessna 172S Skyhawk',
      typeCode: 'C172',
      flown: false,
      rangeText: '640 NM',
      runwayText: '1,630 ft',
    }}
  />
)

/** A glider names no takeoff distance, so the wide card shows the range alone rather than `RWY 0 ft`. */
export const NoRunway = () => (
  <AircraftWidgetCard
    wide
    state={{
      status: 'ready',
      date: '21 Sep',
      name: 'Schleicher ASK 21',
      typeCode: 'AS21',
      flown: false,
      rangeText: '120 NM',
      runwayText: null,
    }}
  />
)

/** No airframes in the fleet yet, even after the auto-seed. */
export const FleetEmpty = () => <AircraftWidgetCard state={{ status: 'fleetEmpty' }} />

/** The fleet could not be read. */
export const Unavailable = () => <AircraftWidgetCard state={{ status: 'unavailable' }} />
