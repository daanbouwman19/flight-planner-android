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
