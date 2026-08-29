import { createRoot } from 'react-dom/client'
import {
  FlightPlannerTheme,
  MetarPanel,
  HeroDistanceCard,
  MetricGrid,
  MonthlyActivityCard,
  RankedListCard,
  VisitedNetworkCard,
  BottomSheet,
  TextField,
  PickerSheet,
  AddAircraftSheet,
  EditEnvelopeSheet,
  AddFlightSheet,
  FlightDatePickerDialog,
  StartupCheckScreen,
  LicencesScreen,
  FleetDetailScreen,
  StatsScreen,
  AirportDetailScreen,
  PhoneFrame,
  ScrimOverlay,
} from '../src/index'

const EUROPE = [
  { icao: 'EHAM', lat: 52.308601, lon: 4.76389, visits: 14 },
  { icao: 'EGLL', lat: 51.470748, lon: -0.459909, visits: 9 },
  { icao: 'EDDF', lat: 50.026706, lon: 8.55835, visits: 6 },
  { icao: 'LFPG', lat: 49.00896, lon: 2.554117, visits: 5 },
  { icao: 'EBBR', lat: 50.901402, lon: 4.48444, visits: 4 },
  { icao: 'LSZH', lat: 47.458056, lon: 8.548056, visits: 3 },
  { icao: 'EKCH', lat: 55.6179, lon: 12.656, visits: 2 },
  { icao: 'LEMD', lat: 40.493407, lon: -3.572249, visits: 4 },
  { icao: 'ESSA', lat: 59.64849, lon: 17.928829, visits: 1 },
]
const LOW_COUNTRIES = [
  { icao: 'EHAM', lat: 52.308601, lon: 4.76389, visits: 14 },
  { icao: 'EHRD', lat: 51.956902, lon: 4.43722, visits: 5 },
  { icao: 'EHEH', lat: 51.4501, lon: 5.37453, visits: 3 },
  { icao: 'EHGG', lat: 53.119107, lon: 6.577652, visits: 2 },
  { icao: 'EBBR', lat: 50.901402, lon: 4.48444, visits: 4 },
]
const legs = [
  { from: [52.308601, 4.76389] as [number, number], to: [51.470748, -0.459909] as [number, number] },
  { from: [52.308601, 4.76389] as [number, number], to: [40.493407, -3.572249] as [number, number] },
  { from: [52.308601, 4.76389] as [number, number], to: [59.64849, 17.928829] as [number, number] },
]

function Cell({ label, w = 400, children }: { label: string; w?: number; children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <div style={{ font: '600 11px system-ui', color: '#888', letterSpacing: '.08em' }}>{label}</div>
      <div style={{ width: w }}>{children}</div>
    </div>
  )
}

const schiphol = [
  { ident: '18R', lengthFt: 12467, widthFt: 198, trueHeadingDeg: 183, latitude: 52.3627, longitude: 4.71193, hardSurface: true },
  { ident: '36L', lengthFt: 12467, widthFt: 198, trueHeadingDeg: 3, latitude: 52.3286, longitude: 4.70884, hardSurface: true },
  { ident: '06', lengthFt: 11283, widthFt: 148, trueHeadingDeg: 58, latitude: 52.2879, longitude: 4.73402, hardSurface: true },
  { ident: '24', lengthFt: 11283, widthFt: 148, trueHeadingDeg: 238, latitude: 52.3046, longitude: 4.77752, hardSurface: true },
]

function App() {
  return (
    <FlightPlannerTheme theme={(new URLSearchParams(location.search).get('t') as any) ?? "brandLight"} fullBleed>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 24, padding: 24, background: 'var(--fp-background)', alignItems: 'flex-start' }}>
        <Cell label="METAR - MARGINAL">
          <MetarPanel
            icao="EHAM"
            flightRules="MVFR"
            skyCover={{ kind: 'layers', layers: [{ cover: 'BROKEN', baseFt: 1800 }, { cover: 'SCATTERED', baseFt: 900 }] }}
            ceilingFt={1800}
            visibilityStatuteMiles={6}
            celestial={{ sunElevationDeg: 34, sunAzimuthDeg: 168 }}
            figures={[
              { label: 'WIND', value: '240 deg 18 kt' },
              { label: 'VIS', value: '6 SM' },
              { label: 'CEIL', value: '1,800 ft' },
              { label: 'QNH', value: '1013 hPa' },
              { label: 'TEMP', value: '14 / 9 C' },
            ]}
            skyLine="Broken at 1,800 ft, scattered at 900 ft"
            observed="1425Z - 12 min ago"
          />
        </Cell>

        <Cell label="METAR - LIFR, RAW OPEN">
          <MetarPanel
            icao="EGLL"
            flightRules="LIFR"
            skyCover={{ kind: 'layers', layers: [{ cover: 'OVERCAST', baseFt: 300 }] }}
            ceilingFt={300}
            fogOrMist
            visibilityStatuteMiles={0.5}
            celestial={{ sunElevationDeg: -18, sunAzimuthDeg: 20, moonElevationDeg: 22, moonAzimuthDeg: 140, moonPhase: 0.5 }}
            figures={[
              { label: 'WIND', value: '000 deg 2 kt' },
              { label: 'VIS', value: '0.5 SM' },
              { label: 'CEIL', value: '300 ft' },
              { label: 'QNH', value: '29.92 inHg' },
            ]}
            skyLine="Overcast at 300 ft, mist"
            observed="0250Z - 8 min ago"
            raw="EGLL 020250Z 00002KT 0800 BR OVC003 04/04 Q1013 NOSIG"
            expanded
          />
        </Cell>

        <Cell label="METAR - STALE / NO REPORT">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <MetarPanel
              icao="ESSA"
              flightRules="IFR"
              skyCover={{ kind: 'layers', layers: [{ cover: 'OVERCAST', baseFt: 700 }] }}
              ceilingFt={700}
              figures={[
                { label: 'WIND', value: '310 deg 21G34 kt' },
                { label: 'VIS', value: '3 SM' },
                { label: 'CEIL', value: '700 ft' },
              ]}
              skyLine="Overcast at 700 ft"
              observed="1120Z - 3 days ago"
              stale
            />
            <MetarPanel icao="EHGG" unavailableText="No weather report for EHGG" />
          </div>
        </Cell>

        <Cell label="STATS CARDS">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <HeroDistanceCard totalDistance="48,213 NM" earthCircumferences="2.2 x around the Earth" />
            <MetricGrid
              metrics={[
                { label: 'FLIGHTS', value: '42' },
                { label: 'HOURS', value: '63:25' },
                { label: 'AIRPORTS', value: '31' },
                { label: 'LONGEST', value: '3,153 NM' },
              ]}
            />
            <MonthlyActivityCard
              months={[
                { label: 'M', value: 2 },
                { label: 'J', value: 5 },
                { label: 'J', value: 7 },
                { label: 'A', value: 9 },
                { label: 'S', value: 4 },
                { label: 'O', value: 6 },
              ]}
            />
            <RankedListCard
              title="Most visited"
              rows={[
                { code: 'EHAM', name: 'Schiphol', count: 14 },
                { code: 'EGLL', name: 'Heathrow', count: 9 },
              ]}
            />
          </div>
        </Cell>

        <Cell label="VISITED NETWORK - EUROPE">
          <VisitedNetworkCard airports={EUROPE} legs={legs} />
        </Cell>

        <Cell label="VISITED NETWORK - ONE REGION">
          <VisitedNetworkCard
            airports={LOW_COUNTRIES}
            legs={[{ from: [52.308601, 4.76389], to: [51.956902, 4.43722] }, { from: [52.308601, 4.76389], to: [53.119107, 6.577652] }]}
          />
        </Cell>

        <Cell label="FIELDS">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <TextField label="Manufacturer" placeholder="Cessna" />
            <TextField label="Variant" value="172S Skyhawk" />
            <TextField label="Range" value="640" suffix="NM" />
            <TextField label="Range" value="0" suffix="NM" error supportingText="Must be above zero" />
          </div>
        </Cell>

        <Cell label="SHEET - AUTO" w={380}>
          <BottomSheet title="Add aircraft" auto>
            <TextField label="Manufacturer" value="Cessna" />
            <TextField label="Variant" value="172S Skyhawk" />
          </BottomSheet>
        </Cell>

        <Cell label="PICKER SHEET">
          <PhoneFrame>
            <ScrimOverlay>
              <PickerSheet
                target="departure"
                query="EH"
                results={[
                  { code: 'EHAM', name: 'Amsterdam Airport Schiphol', detail: 'Amsterdam, Netherlands', rules: 'VFR' },
                  { code: 'EHRD', name: 'Rotterdam The Hague Airport', detail: 'Rotterdam, Netherlands', rules: 'MVFR' },
                  { code: 'EHEH', name: 'Eindhoven Airport', detail: 'Eindhoven, Netherlands', rules: 'VFR' },
                  { code: 'EHGG', name: 'Groningen Airport Eelde', detail: 'Groningen, Netherlands', rules: 'IFR' },
                ]}
              />
            </ScrimOverlay>
          </PhoneFrame>
        </Cell>

        <Cell label="ADD AIRCRAFT - INVALID">
          <PhoneFrame>
            <ScrimOverlay>
              <AddAircraftSheet
                manufacturer="Cessna"
                variant=""
                range="0"
                cruise="124"
                takeoff="1,685"
                errors={{ variant: 'Required', range: 'Must be above zero' }}
              />
            </ScrimOverlay>
          </PhoneFrame>
        </Cell>

        <Cell label="EDIT ENVELOPE">
          <PhoneFrame>
            <ScrimOverlay>
              <EditEnvelopeSheet aircraft="Cessna 172S Skyhawk" range="640" cruise="124" takeoff="1,685" />
            </ScrimOverlay>
          </PhoneFrame>
        </Cell>

        <Cell label="ADD FLIGHT">
          <PhoneFrame>
            <ScrimOverlay>
              <AddFlightSheet
                departure="EHAM"
                destination="EGLL"
                aircraft="Cessna 172S Skyhawk"
                date="29 Aug 2026"
                duration="1:12"
              />
            </ScrimOverlay>
          </PhoneFrame>
        </Cell>

        <Cell label="DATE PICKER">
          <PhoneFrame>
            <ScrimOverlay>
              <FlightDatePickerDialog year={2026} month={7} selectedDay={24} maxDay={29} />
            </ScrimOverlay>
          </PhoneFrame>
        </Cell>

        <Cell label="STARTUP CHECK">
          <StartupCheckScreen
            version="1.4.0 - dataset 2026-08-14"
            checks={[
              { name: 'Airport index', status: 'fail', detail: 'Asset missing: maps/airports.idx' },
              { name: 'World outline', status: 'pass', detail: '122 rings, 4,601 points' },
              { name: 'Runway table', status: 'warn', detail: 'No thresholds for 118 fields' },
              { name: 'User database', status: 'pass', detail: 'schema 7' },
              { name: 'Weather cache', status: 'running' },
            ]}
          />
        </Cell>

        <Cell label="LICENCES">
          <LicencesScreen
            licences={[
              { name: 'AndroidX Compose', licence: 'Apache-2.0', copyright: 'The Android Open Source Project' },
              { name: 'Kotlin Standard Library', licence: 'Apache-2.0', copyright: 'JetBrains s.r.o.' },
              { name: 'OurAirports data', licence: 'Public domain' },
            ]}
          />
        </Cell>

        <Cell label="FLEET DETAIL">
          <FleetDetailScreen
            aircraft={{
              variant: 'Cessna 172S Skyhawk',
              category: 'Single Engine Piston',
              range: '640 NM',
              requiredRunway: '1,685 ft',
              flights: 18,
            }}
            cruiseSpeed="124 kt"
          />
        </Cell>

        <Cell label="AIRPORT DETAIL (rewired)">
          <AirportDetailScreen
            icao="EHAM"
            name="Amsterdam Airport Schiphol"
            location="Amsterdam, Netherlands"
            elevation="-11 ft"
            rules="VFR"
            runways={schiphol}
            wind={{ directionFromDeg: 240, speedKt: 12 }}
            skyCover={{ kind: 'layers', layers: [{ cover: 'FEW', baseFt: 3500 }] }}
            ceilingFt={null}
            celestial={{ sunElevationDeg: 38, sunAzimuthDeg: 195 }}
            visibilityStatuteMiles={10}
            figures={[
              { label: 'WIND', value: '240 deg 12 kt' },
              { label: 'VIS', value: '10+ SM' },
              { label: 'CEIL', value: 'Unlimited' },
              { label: 'QNH', value: '1018 hPa' },
              { label: 'TEMP', value: '18 / 11 C' },
            ]}
            skyLine="Few at 3,500 ft"
            observed="1025Z - 9 min ago"
            metar="EHAM 291025Z 24012KT 9999 FEW035 18/11 Q1018 NOSIG"
          />
        </Cell>

        <Cell label="STATS SCREEN (rewired)">
          <StatsScreen
            totalDistance="48,213 NM"
            earthCircumferences="2.2 x around the Earth"
            metrics={[
              { label: 'FLIGHTS', value: '42' },
              { label: 'HOURS', value: '63:25' },
              { label: 'AIRPORTS', value: '31' },
              { label: 'LONGEST', value: '3,153 NM' },
            ]}
            monthly={[
              { label: 'M', value: 2 },
              { label: 'J', value: 5 },
              { label: 'J', value: 7 },
              { label: 'A', value: 9 },
              { label: 'S', value: 4 },
              { label: 'O', value: 6 },
            ]}
            topAircraft={[
              { name: 'Cessna 172S Skyhawk', flights: 18 },
              { name: 'Diamond DA40 NG', flights: 11 },
            ]}
            topAirports={[
              { icao: 'EHAM', name: 'Schiphol', visits: 14 },
              { icao: 'EGLL', name: 'Heathrow', visits: 9 },
            ]}
            visited={EUROPE}
            visitedLegs={legs}
          />
        </Cell>
      </div>
    </FlightPlannerTheme>
  )
}

createRoot(document.getElementById('root')!).render(<App />)
