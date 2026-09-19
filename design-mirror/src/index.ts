/**
 * A React mirror of Flight Planner's `:core:designsystem`.
 *
 * **Direction of truth is one-way.** The Kotlin design system defines this
 * library; every colour, type slot, corner radius, Expressive shape and motion
 * spring in `tokens.json` is read off the same objects the Android app composes
 * with, by `DesignTokenExport` in `:core:designsystem`, and a change there fails
 * `DesignTokenExportTest` until it is regenerated here. A concept designed with
 * this library comes back to the app as *intent* — a hierarchy, a colour role, a
 * spacing rhythm — never as pixel values transplanted into Compose.
 */

export { FlightPlannerTheme, useFlightTheme, useIsDark, colorRoles } from './theme/FlightPlannerTheme'
export type {
  FlightPlannerThemeProps,
  ThemeChoice,
  ResolvedTheme,
} from './theme/FlightPlannerTheme'

export { tokens } from './tokens/tokens.gen'
export type {
  SchemeName,
  ColorRole,
  TypeSlot,
  ShapeSize,
  MotionToken,
  SkyKey,
  FlightRulesKey,
} from './tokens/tokens.gen'

export { FlightRulesBadge, flightRulesCode, flightRulesDescription } from './components/FlightRulesBadge'
export type { FlightRulesBadgeProps, FlightRules } from './components/FlightRulesBadge'

export { ValueChip } from './components/ValueChip'
export type { ValueChipProps } from './components/ValueChip'

export { ModeSelector } from './components/ModeSelector'
export type { ModeSelectorProps, ModeOption } from './components/ModeSelector'

export { FilterField } from './components/FilterField'
export type { FilterFieldProps } from './components/FilterField'

export { StatSummaryStrip } from './components/StatSummaryStrip'
export type { StatSummaryStripProps, StatTile } from './components/StatSummaryStrip'

export { SkeletonBox, SkeletonCard } from './components/Skeleton'
export type { SkeletonBoxProps, SkeletonCardProps } from './components/Skeleton'

export { EmptyState } from './components/EmptyState'
export type { EmptyStateProps } from './components/EmptyState'

export { ErrorState } from './components/ErrorState'
export type { ErrorStateProps } from './components/ErrorState'

export { MonthHeader } from './components/MonthHeader'
export type { MonthHeaderProps } from './components/MonthHeader'

export { MorphingLoadingIndicator } from './components/MorphingLoadingIndicator'
export type { MorphingLoadingIndicatorProps } from './components/MorphingLoadingIndicator'

export { SwipeActionBackground } from './components/SwipeActionBackground'
export type { SwipeActionBackgroundProps, SwipeActionSide } from './components/SwipeActionBackground'

export { ConfirmationDialog, ScrimOverlay } from './components/ConfirmationDialog'
export type { ConfirmationDialogProps, ScrimOverlayProps } from './components/ConfirmationDialog'

export { RouteMap, WORLD_MAP_LAND_ALPHA, WORLD_MAP_COAST_ALPHA } from './components/RouteMap'
export type { RouteMapProps } from './components/RouteMap'

// Phase G — the 3D globe. In the app it is NASA imagery on a Filament sphere; the
// mirror draws the same `land.outline` through the same camera as an outline
// globe (the runtime has no GPU). See `geo/globeFrame.ts` for the divergence.
export { GlobeView, GLOBE_VB_HEIGHT } from './components/GlobeView'
export type { GlobeViewProps } from './components/GlobeView'
export { GlobeHero, GlobeRouteScene } from './components/GlobeHero'
export type { GlobeHeroProps, GlobeRouteSceneProps, GlobeHeroEnd } from './components/GlobeHero'
export { GlobeNetwork } from './components/GlobeNetwork'
export type { GlobeNetworkProps } from './components/GlobeNetwork'
export {
  GlobeCameraControls,
  GlobeAttribution,
  GLOBE_PLATE_ALPHA,
  GLOBE_CONTROL_SIZE,
} from './components/GlobeChrome'
export type { GlobeCameraControlsProps, GlobeAttributionProps } from './components/GlobeChrome'
export {
  GlobeCamera,
  frameRoute,
  framePoints,
  projectArc,
  projectOutline,
  projectPoint,
  latLonToWorld,
  GLOBE_FOV_Y,
} from './geo/globeFrame'
export type { GlobeViewport, ScreenPoint, Vec3, WorldOutlineLike } from './geo/globeFrame'
export { ImmersiveGlobeScreen } from './screens/ImmersiveGlobeScreen'
export type { ImmersiveGlobeScreenProps } from './screens/ImmersiveGlobeScreen'

export { ChallengeWidgetCard } from './components/ChallengeWidget'
export type {
  ChallengeWidgetCardProps,
  ChallengeWidgetState,
  ChallengeWidgetReady,
  ChallengeWidgetFleetEmpty,
  ChallengeWidgetUnavailable,
} from './components/ChallengeWidget'

export { AircraftWidgetCard } from './components/AircraftWidget'
export type {
  AircraftWidgetCardProps,
  AircraftWidgetState,
  AircraftWidgetReady,
  AircraftWidgetFleetEmpty,
  AircraftWidgetUnavailable,
} from './components/AircraftWidget'

export { MapFrame, sampleGeoArc, distanceNm, MIN_SPAN_DEGREES, PADDING_FRACTION } from './geo/mapFrame'
export type { GeoArc, ProjectedRings, ProjectedLand, WorldOutline } from './geo/mapFrame'
export { worldOutline } from './geo/worldOutline.gen'

export { RunwayDiagram } from './components/RunwayDiagram'
export type { RunwayDiagramProps, DiagramWind, Runway } from './components/RunwayDiagram'

export {
  pairPhysicalRunways,
  layoutRunways,
  positionedRays,
  laneRay,
  projectLocal,
  favouredEnd,
  windComponents,
  sockLift,
} from './geo/runwayLayout'
export type { RunwayRay, PhysicalRunway, WindComponents, Point } from './geo/runwayLayout'

export { RouteCard } from './components/RouteCard'
export type { RouteCardProps, RouteEndpoint } from './components/RouteCard'

export { SkyProfile, SkyProfileHeight } from './components/SkyProfile'
export type { SkyProfileProps, CelestialState } from './components/SkyProfile'

export {
  altitudeToFraction,
  mergeDecks,
  deckFractions,
  deckSpans,
  deckOpacity,
  fogHeightFraction,
  celestialAlpha,
  skyBlendFor,
  railX,
  railY,
  AXIS_BREAKPOINTS,
  CEILING_THRESHOLDS,
} from './geo/skyProfile'
export type { SkyCover, CloudLayer, CloudDeck, CloudCover, SkyPhase } from './geo/skyProfile'

export { NavigationBar, TopAppBar, PhoneFrame, NavIcon, navIconPaths } from './components/AppChrome'
export type { NavigationBarProps, TopAppBarProps, PhoneFrameProps, NavIconProps, NavDestination } from './components/AppChrome'

// Screens — the app as built, so a concept starts from what exists.
export { PlanScreen } from './screens/PlanScreen'
export type { PlanScreenProps } from './screens/PlanScreen'
export { FleetScreen } from './screens/FleetScreen'
export type { FleetScreenProps, Aircraft } from './screens/FleetScreen'
export { LogbookScreen } from './screens/LogbookScreen'
export type { LogbookScreenProps, LogbookMonth, LoggedFlight } from './screens/LogbookScreen'
export { StatsScreen } from './screens/StatsScreen'
export type { StatsScreenProps } from './screens/StatsScreen'
export { AirportsScreen } from './screens/AirportsScreen'
export type { AirportsScreenProps, AirportRow } from './screens/AirportsScreen'
export { AirportDetailScreen } from './screens/AirportDetailScreen'
export type { AirportDetailScreenProps } from './screens/AirportDetailScreen'
export { RouteDetailScreen } from './screens/RouteDetailScreen'
export type { RouteDetailScreenProps, RouteDetailEnd } from './screens/RouteDetailScreen'
export { SettingsScreen } from './screens/SettingsScreen'
export type { SettingsScreenProps } from './screens/SettingsScreen'

// Wide-window chrome. The app has one set of screens that adapt, so these are
// the pieces the wide form swaps in rather than a parallel screen set.
export { NavigationRail } from './components/NavigationRail'
export type { NavigationRailProps, RailDestination } from './components/NavigationRail'
export { TabletFrame, TwoPaneScaffold, MAX_CONTENT_WIDTH, WIDE_MAX_CONTENT_WIDTH } from './components/TabletFrame'
export type { TabletFrameProps, TwoPaneScaffoldProps } from './components/TabletFrame'
export { RouteDetailPane } from './screens/RouteDetailScreen'
export type { RouteDetailPaneProps } from './screens/RouteDetailScreen'
export { FleetDetailPane } from './screens/FleetScreen'
export type { FleetDetailPaneProps } from './screens/FleetScreen'
export { FlightDetailPane } from './screens/LogbookScreen'
export type { FlightDetailPaneProps } from './screens/LogbookScreen'
export type { ScreenLayout } from './screens/PlanScreen'

// Sheets, the dialog, the utility screens and the statistics cards.
export { BottomSheet, TextField } from './components/BottomSheet'
export type { BottomSheetProps, TextFieldProps } from './components/BottomSheet'
export { PickerSheet, AddAircraftSheet, EditEnvelopeSheet, AddFlightSheet } from './components/Sheets'
export type { PickerSheetProps, PickerResult, AddAircraftSheetProps, EditEnvelopeSheetProps, AddFlightSheetProps } from './components/Sheets'
export { FlightDatePickerDialog } from './components/FlightDatePickerDialog'
export type { FlightDatePickerDialogProps } from './components/FlightDatePickerDialog'
export { HeroDistanceCard, MetricGrid, MonthlyActivityCard, RankedListCard, VisitedNetworkCard } from './components/StatsCards'
export type { HeroDistanceCardProps, MetricGridProps, MonthlyActivityCardProps, RankedListCardProps, RankedRow, VisitedNetworkCardProps, VisitedAirport, VisitedLeg } from './components/StatsCards'
export { StartupCheckScreen, LicencesScreen } from './screens/UtilityScreens'
export type { StartupCheckScreenProps, StartupCheck, CheckStatus, LicencesScreenProps, Licence } from './screens/UtilityScreens'
export { FleetDetailScreen } from './screens/FleetDetailScreen'
export type { FleetDetailScreenProps } from './screens/FleetDetailScreen'

export { MetarPanel } from './components/MetarPanel'
export type { MetarPanelProps, MetarFigure } from './components/MetarPanel'
