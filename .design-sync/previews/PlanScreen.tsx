import { FlightPlannerTheme, PlanScreen, RouteDetailPane } from '@flightplanner/design-mirror'

// Airports, coordinates and runway lengths are real, from the app's airports.db.

const routes = [
  {
    aircraft: 'Cessna 172S Skyhawk',
    category: 'Single Engine Piston',
    departure: { icao: 'EHAM', lat: 52.3086, lon: 4.76389, runway: '12,467 ft', rules: 'VFR' as const },
    destination: { icao: 'EBBR', lat: 50.9014, lon: 4.48444, runway: '11,936 ft', rules: 'VFR' as const },
    distance: '92 NM',
    flightTime: '0:52',
  },
  {
    aircraft: 'Cessna 172S Skyhawk',
    category: 'Single Engine Piston',
    departure: { icao: 'EHRD', lat: 51.9569, lon: 4.43722, runway: '7,218 ft', rules: 'MVFR' as const },
    destination: { icao: 'EGLL', lat: 51.4707, lon: -0.459909, runway: '12,799 ft', rules: 'IFR' as const },
    distance: '187 NM',
    flightTime: '1:46',
  },
  {
    aircraft: 'Cessna 172S Skyhawk',
    category: 'Single Engine Piston',
    departure: { icao: 'EHAM', lat: 52.3086, lon: 4.76389, runway: '12,467 ft', rules: 'VFR' as const },
    destination: { icao: 'EDDF', lat: 50.0267, lon: 8.55835, runway: '13,123 ft', rules: 'VFR' as const },
    distance: '203 NM',
    flightTime: '1:55',
  },
]

/** The screen as it usually looks: a filter set, a mode chosen, three routes. */
export const Default = () => (
  <PlanScreen
    routes={routes}
    selectedMode={1}
    notFlownCount={116}
    departure={{ icao: 'EHAM', name: 'Amsterdam Airport Schiphol' }}
    aircraft={{ variant: 'C172S', envelope: '640 NM · 1,685 ft' }}
  />
)

/**
 * Nothing filtered: both fields unset and every route in scope.
 *
 * The unset fields recede into their outline rather than filling, because an unset
 * filter is the absence of a constraint and the field doing something to the list
 * is the one that should have the contrast.
 */
export const NoFilters = () => (
  <PlanScreen routes={routes} selectedMode={0} notFlownCount={116} />
)

/** The same screen in Cockpit, for a night departure. */
export const Cockpit = () => (
  <FlightPlannerTheme theme="cockpit">
    <PlanScreen
      routes={routes}
      selectedMode={1}
      notFlownCount={116}
      departure={{ icao: 'EHAM', name: 'Amsterdam Airport Schiphol' }}
      aircraft={{ variant: 'C172S', envelope: '640 NM · 1,685 ft' }}
    />
  </FlightPlannerTheme>
)

/** And in Chart — printed chart paper and navy ink. */
export const Chart = () => (
  <FlightPlannerTheme theme="chart">
    <PlanScreen
      routes={routes}
      selectedMode={0}
      notFlownCount={116}
      aircraft={{ variant: 'PC-12', envelope: '1,803 NM · 2,438 ft' }}
    />
  </FlightPlannerTheme>
)

/**
 * The wide window: a rail instead of a bar, the list capped rather than stretched,
 * and the route's detail beside it instead of on a screen of its own.
 *
 * The rail carries **five** destinations where the bar carries four — Settings has
 * room here, so it gets a permanent home rather than hiding in the app bar.
 */
export const Tablet = () => (
  <PlanScreen
    layout="tablet"
    routes={routes}
    selectedMode={1}
    notFlownCount={116}
    departure={{ icao: 'EHAM', name: 'Amsterdam Airport Schiphol' }}
    aircraft={{ variant: 'C172S', envelope: '640 NM · 1,685 ft' }}
    detail={
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
          skyCover: { kind: 'layers', layers: [{ cover: 'FEW', baseFt: 4200 }] },
          ceilingFt: null,
          celestial: { sunElevationDeg: 44, sunAzimuthDeg: 180 },
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
        }}
      />
    }
  />
)

/**
 * A wide window with nothing selected yet.
 *
 * The list takes the whole content area and is still width-capped: past about
 * 640px a single column stops helping, so the extra width becomes margin. It is
 * deliberately not a grid.
 */
export const TabletNoSelection = () => (
  <PlanScreen
    layout="tablet"
    routes={routes}
    selectedMode={0}
    notFlownCount={116}
  />
)

/** The wide layout in Cockpit, for a night flight deck. */
export const TabletCockpit = () => (
  <FlightPlannerTheme theme="cockpit">
    <PlanScreen
      layout="tablet"
      routes={routes}
      selectedMode={1}
      notFlownCount={116}
      aircraft={{ variant: 'C172S', envelope: '640 NM · 1,685 ft' }}
    />
  </FlightPlannerTheme>
)
