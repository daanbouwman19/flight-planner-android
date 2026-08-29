import { FlightPlannerTheme, MetarPanel } from '@flightplanner/design-mirror'

/**
 * A field in marginal conditions.
 *
 * The order is the argument: the sky drawn, then the sky in figures, then the sky
 * in words. The category is a *consequence of the geometry* above it rather than a
 * label pinned beside a cartoon, so there is no arrangement of this panel that
 * shows a 1,800 ft broken deck as a nice day.
 */
export const Marginal = () => (
  <div style={{ width: 380 }}>
    <MetarPanel
      icao="EHAM"
      flightRules="MVFR"
      skyCover={{ kind: 'layers', layers: [{ cover: 'BROKEN', baseFt: 1800 }, { cover: 'SCATTERED', baseFt: 900 }] }}
      ceilingFt={1800}
      visibilityStatuteMiles={6}
      celestial={{ sunElevationDeg: 34, sunAzimuthDeg: 168 }}
      figures={[
        { label: 'WIND', value: '240° 18 kt' },
        { label: 'VIS', value: '6 SM' },
        { label: 'CEIL', value: '1,800 ft' },
        { label: 'QNH', value: '1013 hPa' },
        { label: 'TEMP', value: '14 / 9 °C' },
      ]}
      skyLine="Broken at 1,800 ft, scattered at 900 ft"
      observed="1425Z · 12 min ago"
    />
  </div>
)

/**
 * A clear day, and the short last row that keeps its hole.
 *
 * A station that sends no altimeter shows four chips rather than a fifth reading
 * `—`: the empty figure is for a value that should exist and does not, and a group
 * a station never sends is not that. The gap in the last row is deliberate — a QNH
 * chip stretched to twice the width of the CEIL above it would read as the more
 * important figure.
 */
export const ClearDay = () => (
  <div style={{ width: 380 }}>
    <MetarPanel
      icao="LEMD"
      flightRules="VFR"
      skyCover={{ kind: 'clear' }}
      ceilingFt={null}
      visibilityStatuteMiles={10}
      celestial={{ sunElevationDeg: 58, sunAzimuthDeg: 200 }}
      figures={[
        { label: 'WIND', value: '070° 6 kt' },
        { label: 'VIS', value: '10+ SM' },
        { label: 'CEIL', value: 'Unlimited' },
      ]}
      skyLine="Clear below 12,000 ft"
      observed="1450Z · 4 min ago"
    />
  </div>
)

/**
 * Low IFR at night, with the raw report open.
 *
 * The raw text is collapsed by default. Forty characters of monospace under every
 * panel — twice over on the route screen — pushed everything that is read at a
 * glance off the top of it.
 */
export const LowIfrExpanded = () => (
  <div style={{ width: 380 }}>
    <MetarPanel
      icao="EGLL"
      flightRules="LIFR"
      skyCover={{ kind: 'layers', layers: [{ cover: 'OVERCAST', baseFt: 300 }] }}
      ceilingFt={300}
      fogOrMist
      visibilityStatuteMiles={0.5}
      celestial={{ sunElevationDeg: -18, sunAzimuthDeg: 20, moonElevationDeg: 22, moonAzimuthDeg: 140, moonPhase: 0.5 }}
      figures={[
        { label: 'WIND', value: '000° 2 kt' },
        { label: 'VIS', value: '0.5 SM' },
        { label: 'CEIL', value: '300 ft' },
        { label: 'QNH', value: '29.92 inHg' },
      ]}
      skyLine="Overcast at 300 ft, mist"
      observed="0250Z · 8 min ago"
      raw="EGLL 020250Z 00002KT 0800 BR OVC003 04/04 Q1013 NOSIG"
      expanded
    />
  </div>
)

/**
 * A report past its currency.
 *
 * The staleness is stated **on the reading** rather than as a banner over the
 * panel, because it is a fact about the reading. The scene above still draws the
 * report it belongs to, not the present sky.
 */
export const Stale = () => (
  <div style={{ width: 380 }}>
    <MetarPanel
      icao="ESSA"
      flightRules="IFR"
      skyCover={{ kind: 'layers', layers: [{ cover: 'OVERCAST', baseFt: 700 }] }}
      ceilingFt={700}
      visibilityStatuteMiles={3}
      figures={[
        { label: 'WIND', value: '310° 21G34 kt' },
        { label: 'VIS', value: '3 SM' },
        { label: 'CEIL', value: '700 ft' },
      ]}
      skyLine="Overcast at 700 ft"
      observed="1120Z · 3 days ago"
      stale
    />
  </div>
)

/**
 * No report at all.
 *
 * The scene hatches rather than drawing a clear sky, and the panel says which
 * station it has nothing for. An unreported sky and a clear sky are different
 * facts and must not look alike.
 */
export const NoReport = () => (
  <div style={{ width: 380 }}>
    <MetarPanel icao="EHGG" unavailableText="No weather report for EHGG" />
  </div>
)

/** In Chart, where the cross-section is drawn as a plate rather than a screen. */
export const Chart = () => (
  <FlightPlannerTheme theme="chart">
    <div style={{ width: 380 }}>
      <MetarPanel
        icao="LFPG"
        flightRules="VFR"
        skyCover={{ kind: 'layers', layers: [{ cover: 'FEW', baseFt: 4500 }] }}
        ceilingFt={null}
        visibilityStatuteMiles={10}
        celestial={{ sunElevationDeg: 41, sunAzimuthDeg: 210 }}
        figures={[
          { label: 'WIND', value: '190° 11 kt' },
          { label: 'VIS', value: '10+ SM' },
          { label: 'CEIL', value: 'Unlimited' },
          { label: 'QNH', value: '1018 hPa' },
        ]}
        skyLine="Few at 4,500 ft"
        observed="1420Z · 6 min ago"
      />
    </div>
  </FlightPlannerTheme>
)
