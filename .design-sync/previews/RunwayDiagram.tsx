import { RunwayDiagram } from '@flightplanner/design-mirror'

// Every coordinate below is real, read out of the app's own bundled
// `airports.db` rather than recalled — an invented threshold produces a diagram
// that is confidently the wrong shape, which is the one failure this component
// must not have.

/**
 * Schiphol, from its published threshold coordinates: six parallel-ish strips in
 * their true relative positions.
 *
 * This is the layout that exists only because OurAirports publishes a latitude and
 * longitude for every end. A compass schematic would draw all twelve ends through
 * one point and lose the field's actual shape — the Polderbaan sitting far out to
 * the north-west, the Kaagbaan cutting across the south.
 */
export const PositionedSchiphol = () => (
  <RunwayDiagram
    runways={[
      { ident: '04', lengthFt: 6627, widthFt: 148, trueHeadingDeg: 41, latitude: 52.3004, longitude: 4.78348, hardSurface: true },
      { ident: '22', lengthFt: 6627, widthFt: 148, trueHeadingDeg: 221, latitude: 52.314, longitude: 4.80302, hardSurface: true },
      { ident: '06', lengthFt: 11283, widthFt: 148, trueHeadingDeg: 58, latitude: 52.2879, longitude: 4.73402, hardSurface: true },
      { ident: '24', lengthFt: 11283, widthFt: 148, trueHeadingDeg: 238, latitude: 52.3046, longitude: 4.77752, hardSurface: true },
      { ident: '09', lengthFt: 11329, widthFt: 148, trueHeadingDeg: 87, latitude: 52.3166, longitude: 4.74635, hardSurface: true },
      { ident: '27', lengthFt: 11329, widthFt: 148, trueHeadingDeg: 267, latitude: 52.3184, longitude: 4.79689, hardSurface: true },
      { ident: '18R', lengthFt: 12467, widthFt: 198, trueHeadingDeg: 183, latitude: 52.3627, longitude: 4.71193, hardSurface: true },
      { ident: '36L', lengthFt: 12467, widthFt: 198, trueHeadingDeg: 3, latitude: 52.3286, longitude: 4.70884, hardSurface: true },
      { ident: '18C', lengthFt: 10826, widthFt: 148, trueHeadingDeg: 183, latitude: 52.3314, longitude: 4.74003, hardSurface: true },
      { ident: '36C', lengthFt: 10826, widthFt: 148, trueHeadingDeg: 3, latitude: 52.3018, longitude: 4.7375, hardSurface: true },
      { ident: '18L', lengthFt: 11155, widthFt: 148, trueHeadingDeg: 183, latitude: 52.3213, longitude: 4.77996, hardSurface: true },
      { ident: '36R', lengthFt: 11155, widthFt: 148, trueHeadingDeg: 3, latitude: 52.2908, longitude: 4.77735, hardSurface: true },
    ]}
  />
)

/**
 * Heathrow with a westerly — two parallel strips, and the wind picking an end.
 *
 * The halo says *which strip* and the bold sock-orange numeral says *which end of
 * it*, which is the question a pilot is actually asking. The sock streams away
 * from where the wind comes from, and its length is the speed.
 */
export const WithWind = () => (
  <RunwayDiagram
    runways={[
      { ident: '09L', lengthFt: 12799, widthFt: 164, trueHeadingDeg: 90, latitude: 51.47749, longitude: -0.489439, hardSurface: true },
      { ident: '27R', lengthFt: 12799, widthFt: 164, trueHeadingDeg: 270, latitude: 51.477681, longitude: -0.433227, hardSurface: true },
      { ident: '09R', lengthFt: 12001, widthFt: 164, trueHeadingDeg: 90, latitude: 51.46478, longitude: -0.486808, hardSurface: true },
      { ident: '27L', lengthFt: 12001, widthFt: 164, trueHeadingDeg: 270, latitude: 51.464957, longitude: -0.434048, hardSurface: true },
    ]}
    wind={{ directionFromDeg: 250, speedKt: 14 }}
  />
)

/**
 * Edwards Air Force Base with its coordinates withheld — the compass schematic.
 *
 * This is the field that forced the lane fan to exist. Its three near-parallel
 * strips, drawn through one shared centre, became a starburst through a point that
 * does not exist. Roughly parallel families are fanned into evenly spaced lanes
 * instead, so the diagram states "three parallel strips" rather than a star.
 *
 * The real field does publish coordinates; they are dropped here to exercise the
 * fallback, which is the layout most small fields actually get.
 */
export const LaneSchematic = () => (
  <RunwayDiagram
    runways={[
      { ident: '05L', lengthFt: 12000, widthFt: 200, trueHeadingDeg: 50, hardSurface: true },
      { ident: '23R', lengthFt: 12000, widthFt: 200, trueHeadingDeg: 230, hardSurface: true },
      { ident: '05R', lengthFt: 16798, widthFt: 300, trueHeadingDeg: 58, hardSurface: true },
      { ident: '23L', lengthFt: 16798, widthFt: 300, trueHeadingDeg: 238, hardSurface: true },
      { ident: '07', lengthFt: 8000, widthFt: 50, trueHeadingDeg: 77, hardSurface: true },
      { ident: '25', lengthFt: 8000, widthFt: 50, trueHeadingDeg: 257, hardSurface: true },
    ]}
  />
)

/**
 * A grass strip with a variable wind, and one end with no published heading.
 *
 * Soft surfaces draw in the variant ink. The undiagrammable end is counted
 * underneath rather than silently dropped — an absence a reader can see is a
 * different thing from one they cannot. A `VRB` group is a real speed with no
 * usable direction, so the sock reports the speed and says so.
 */
export const SoftFieldWithUnknownEnd = () => (
  <RunwayDiagram
    runways={[
      { ident: '11', lengthFt: 2300, trueHeadingDeg: 112, hardSurface: false },
      { ident: '29', lengthFt: 2300, trueHeadingDeg: 292, hardSurface: false },
      { ident: '05', lengthFt: 1800, hardSurface: false },
    ]}
    wind={{ speedKt: 6, variable: true }}
  />
)
