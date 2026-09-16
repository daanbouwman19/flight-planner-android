import { useState } from 'react'
import { PhoneFrame, TopAppBar } from '../components/AppChrome'
import { FlightRulesBadge, type FlightRules } from '../components/FlightRulesBadge'
import { RouteMap } from '../components/RouteMap'
import { GlobeHero } from '../components/GlobeHero'
import { SkyProfileHeight, type CelestialState } from '../components/SkyProfile'
import { MetarPanel, type MetarFigure } from '../components/MetarPanel'
import { ValueChip } from '../components/ValueChip'
import type { SkyCover } from '../geo/skyProfile'

export interface RouteDetailEnd {
  icao: string
  name: string
  lat: number
  lon: number
  runway: string
  rules?: FlightRules
  skyCover?: SkyCover
  ceilingFt?: number | null
  celestial?: CelestialState | null
  fogOrMist?: boolean
  visibilityStatuteMiles?: number | null
  /** The report's figures, already formatted and already in the reader's units. */
  figures?: MetarFigure[]
  /** The sky in words — what the cross-section draws, said plainly. */
  skyLine?: string
  /** `Observed 1425Z · 12 min ago`. */
  observed?: string
  /** Draws the age in the error colour, for a report past its currency. */
  stale?: boolean
  /** The raw report, behind a tap. */
  raw?: string
}

export interface RouteDetailPaneProps {
  aircraft: string
  departure: RouteDetailEnd
  destination: RouteDetailEnd
  /** Already formatted — `3,153 NM`. */
  distance: string
  /** Already formatted — `7:04`. */
  flightTime: string
  /** Already formatted — `271°`. */
  bearing?: string
  /**
   * `outline` draws the flat `RouteMap` hero this screen has always shown;
   * `none` draws nothing, for when the host draws its own hero above the content
   * (the globe mode of {@link RouteDetailScreen}). Matches `hero = EmptyHero` in
   * the app's `RouteDetailContent`.
   */
  hero?: 'outline' | 'none'
  className?: string
}

/**
 * One route's detail, without a frame around it.
 *
 * On a phone this is the body of {@link RouteDetailScreen}; on a tablet it is the
 * trailing half of Plan's two-pane layout. It is one component in both places
 * because in the app the card's face travels here as a shared element — two
 * components that merely looked alike would come apart mid-transition.
 *
 * The airport **names** appear here, where there is room to read them. They are
 * deliberately absent from the route card, where every one of them truncated.
 */
export function RouteDetailPane({
  aircraft,
  departure,
  destination,
  distance,
  flightTime,
  bearing,
  hero = 'outline',
  className,
}: RouteDetailPaneProps) {
  return (
    <div className={['fp-screen', 'fp-content-cap', 'fp-content-cap--wide', className].filter(Boolean).join(' ')}>
      <div className="fp-screen__list">
        {hero === 'outline' && (
          <div className="fp-detail-hero">
            <RouteMap
              depLat={departure.lat}
              depLon={departure.lon}
              destLat={destination.lat}
              destLon={destination.lon}
              aspect={360 / 200}
            />
          </div>
        )}

        <div className="fp-detail-codes">
          <div className="fp-detail-code">
            {departure.rules != null && <FlightRulesBadge rules={departure.rules} />}
            <span className="fp-detail-code__icao fp-type-headline-medium">{departure.icao}</span>
            <span className="fp-detail-code__name fp-type-label-small">{departure.name}</span>
          </div>
          <div className="fp-detail-code fp-detail-code--end">
            {destination.rules != null && <FlightRulesBadge rules={destination.rules} />}
            <span className="fp-detail-code__icao fp-type-headline-medium">{destination.icao}</span>
            <span className="fp-detail-code__name fp-type-label-small">{destination.name}</span>
          </div>
        </div>

        <div className="fp-detail-facts">
          <ValueChip label="DIST" value={distance} />
          <ValueChip label="TIME" value={flightTime} />
          {bearing != null && <ValueChip label="BRG" value={bearing} />}
          <ValueChip label="ACFT" value={aircraft} />
        </div>

        {/*
            One panel per end, in flight order. They are the same component the
            airport screen uses, so a station read here and a station read there
            are the same reading rather than two arrangements of the same facts.
          */}
        {departure.skyCover != null && (
          <MetarPanel {...endWeather(departure)} sceneHeight={SkyProfileHeight.RouteDetail} />
        )}

        {destination.skyCover != null && (
          <MetarPanel {...endWeather(destination)} sceneHeight={SkyProfileHeight.RouteDetail} />
        )}
      </div>
    </div>
  )
}

export interface RouteDetailScreenProps extends RouteDetailPaneProps {
  /**
   * Which drawing of the leg the hero shows. `outline` is the flat map this
   * screen has always opened with; `globe` takes the **deep hero** — 44 % of the
   * window, full bleed under the status bar. Outline first, because the sphere
   * costs a renderer and a network and puts a photograph under the clock, and
   * none of that should be the price of opening a route. `HeroMode` in the app.
   */
  heroMode?: 'outline' | 'globe'
  /**
   * Whether the device can draw a globe at all. When false the Flat/Globe switch
   * and the fullscreen action are **absent, not disabled** — a control that
   * opens nothing is worse than no control (3B). Default true.
   */
  globeAvailable?: boolean
  /** Opens the immersive globe. Absent where there is no renderer. */
  onOpenImmersiveGlobe?: () => void
}

/**
 * One route in full, as its own screen.
 *
 * This is the phone form: a detail screen is not a section, so it takes the whole
 * window and the navigation is suppressed while it is up. On a tablet the same
 * content appears as {@link RouteDetailPane} beside Plan's list instead — the
 * globe hero is phone-screen only, matching the app.
 *
 * The Flat/Globe switch lives in the app bar and holds its own state. In globe
 * mode the bar goes to glass, the hero runs full bleed under it and the clock,
 * and a fullscreen action appears beside the switch — it is about the globe, so
 * it appears with it.
 */
export function RouteDetailScreen({
  className,
  heroMode = 'outline',
  globeAvailable = true,
  onOpenImmersiveGlobe,
  ...pane
}: RouteDetailScreenProps) {
  const [mode, setMode] = useState<'outline' | 'globe'>(heroMode)
  const showGlobe = mode === 'globe' && globeAvailable

  const heroSwitch = globeAvailable && (
    <button
      type="button"
      className="fp-app-bar__action"
      onClick={() => setMode(showGlobe ? 'outline' : 'globe')}
      aria-label={showGlobe ? 'Show the outline hero' : 'Show the globe'}
    >
      {showGlobe ? <OutlineHeroIcon /> : <GlobeHeroIcon />}
    </button>
  )
  const fullscreen = showGlobe && (
    <button
      type="button"
      className="fp-app-bar__action"
      onClick={onOpenImmersiveGlobe}
      aria-label="Open the globe fullscreen"
    >
      <FullscreenIcon />
    </button>
  )

  const bar = (
    <TopAppBar
      title={`${pane.departure.icao} → ${pane.destination.icao}`}
      onBack={() => {}}
      onGlass={showGlobe}
      actions={
        <>
          {heroSwitch}
          {fullscreen}
        </>
      }
    />
  )

  if (showGlobe) {
    return (
      <PhoneFrame className={className}>
        <div className="fp-route-detail-globe">
          <div className="fp-route-detail-globe__hero">
            <GlobeHero
              departure={pane.departure}
              destination={pane.destination}
              topChromeFraction={0.17}
            />
          </div>
          <div className="fp-route-detail-globe__bar">{bar}</div>
          <RouteDetailPane {...pane} hero="none" />
        </div>
      </PhoneFrame>
    )
  }

  return (
    <PhoneFrame className={className}>
      {bar}
      <RouteDetailPane {...pane} />
    </PhoneFrame>
  )
}

/**
 * The weather half of a {@link RouteDetailEnd}, as {@link MetarPanel}'s props.
 *
 * Split out so the two ends cannot pick up different sets of fields, which is
 * exactly what happened while each end had its own block of markup.
 */
function endWeather(end: RouteDetailEnd) {
  return {
    icao: end.icao,
    flightRules: end.rules,
    skyCover: end.skyCover,
    ceilingFt: end.ceilingFt,
    fogOrMist: end.fogOrMist,
    visibilityStatuteMiles: end.visibilityStatuteMiles,
    celestial: end.celestial,
    figures: end.figures,
    skyLine: end.skyLine,
    observed: end.observed,
    stale: end.stale,
    raw: end.raw,
  }
}

/** A flat map in a frame — the outline hero, drawn as an app-bar glyph. */
function OutlineHeroIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="3" y="5" width="18" height="14" rx="2" stroke="currentColor" strokeWidth="2" />
      <path d="M6 15 L11 9 L15 13 L18 10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

/** A meridianed circle — the globe hero. */
function GlobeHeroIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2" />
      <ellipse cx="12" cy="12" rx="4" ry="9" stroke="currentColor" strokeWidth="2" />
      <path d="M3 12 H21" stroke="currentColor" strokeWidth="2" />
    </svg>
  )
}

function FullscreenIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z" />
    </svg>
  )
}
