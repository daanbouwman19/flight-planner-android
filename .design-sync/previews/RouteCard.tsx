import { FlightPlannerTheme, RouteCard } from '@flightplanner/design-mirror'

// Coordinates and runway lengths are real, from the app's own airports.db.

/** A short VFR hop in a trainer — the everyday case the Plan screen generates. */
export const Everyday = () => (
  <div style={{ width: 360 }}>
    <RouteCard
      aircraft="Cessna 172S Skyhawk"
      category="Single Engine Piston"
      departure={{ icao: 'EHAM', lat: 52.3086, lon: 4.7639, runway: '12,467 ft', rules: 'VFR' }}
      destination={{ icao: 'EGLL', lat: 51.4706, lon: -0.4619, runway: '12,799 ft', rules: 'MVFR' }}
      distance="199 NM"
      flightTime="1:52"
    />
  </div>
)

/**
 * A long-haul leg with weather at both ends.
 *
 * The flight-rules chips are the only saturated colour on the card apart from the
 * route itself, which is what makes them scannable down a list.
 */
export const LongHaul = () => (
  <div style={{ width: 360 }}>
    <RouteCard
      aircraft="Boeing 777-300ER"
      category="Jet"
      departure={{ icao: 'EHAM', lat: 52.3086, lon: 4.7639, runway: '12,467 ft', rules: 'IFR' }}
      destination={{ icao: 'KJFK', lat: 40.6398, lon: -73.7789, runway: '14,511 ft', rules: 'LIFR' }}
      distance="3,153 NM"
      flightTime="7:04"
    />
  </div>
)

/**
 * A destination whose longest runway is shorter than the aircraft needs.
 *
 * The colour carries the warning rather than a separate icon, so the figure and
 * the judgement of it are the same glyphs.
 */
export const RunwayTooShort = () => (
  <div style={{ width: 360 }}>
    <RouteCard
      aircraft="Pilatus PC-12"
      category="Single Engine Turboprop"
      departure={{ icao: 'LSZH', lat: 47.4647, lon: 8.5492, runway: '12,139 ft', rules: 'VFR' }}
      destination={{
        icao: 'LSGS',
        lat: 46.2196,
        lon: 7.3268,
        runway: '2,001 ft',
        runwayTooShort: true,
        rules: 'MVFR',
      }}
      distance="63 NM"
      flightTime="0:31"
    />
  </div>
)

/** Weather not yet loaded: the category slot holds its space rather than reflowing. */
export const WeatherPending = () => (
  <div style={{ width: 360 }}>
    <RouteCard
      aircraft="Diamond DA40 NG"
      category="Single Engine Piston"
      departure={{ icao: 'ESSA', lat: 59.6519, lon: 17.9186, runway: '10,830 ft' }}
      destination={{ icao: 'EFHK', lat: 60.3172, lon: 24.9633, runway: '11,286 ft' }}
      distance="209 NM"
      flightTime="1:44"
    />
  </div>
)

/** The same card in Cockpit, the night-flying theme. */
export const Cockpit = () => (
  <FlightPlannerTheme theme="cockpit" style={{ padding: 16, width: 360 }}>
    <RouteCard
      aircraft="Cirrus SR22T"
      category="Single Engine Piston"
      departure={{ icao: 'LFPG', lat: 49.0097, lon: 2.5479, runway: '13,829 ft', rules: 'VFR' }}
      destination={{ icao: 'LIRF', lat: 41.8003, lon: 12.2389, runway: '12,795 ft', rules: 'VFR' }}
      distance="595 NM"
      flightTime="3:12"
    />
  </FlightPlannerTheme>
)
