import type { CSSProperties, ReactNode } from 'react'
import { useId } from 'react'
import { GlobeCamera, projectOutline, type GlobeViewport } from '../geo/globeFrame'
import { worldOutline } from '../geo/worldOutline.gen'

/** The viewBox the globe scenes are projected through. Height fixed, width from aspect. */
export const GLOBE_VB_HEIGHT = 1000

/** Land on the sphere, a touch heavier than the flat map's 8 % so it reads on the disc. */
const LAND_ALPHA = 0.1
const COAST_ALPHA = 0.2
/** `AtmosphereAlpha` in `:feature:globe`'s GlobeSurface.kt. */
const ATMOSPHERE_ALPHA = 0.3

export interface GlobeViewProps {
  camera: GlobeCamera
  /** viewBox aspect (width / height). The element fills its parent box. */
  aspect: number
  /** SVG drawn on the sphere, between the coastline and the limb — the arc, the markers. */
  scene?: ReactNode
  /** HTML positioned over the glass — label plates, the camera stack, the credit. */
  overlay?: ReactNode
  className?: string
  style?: CSSProperties
}

/**
 * The sphere itself: the world's coastline projected through {@link GlobeCamera},
 * clipped to the limb, over the theme's own high-altitude day sky.
 *
 * This is the shared base of {@link GlobeHero} and {@link GlobeNetwork}. In the
 * app the disc is NASA imagery on a Filament sphere; here it is the same
 * `land.outline` the phone reads, drawn as an outline globe — a recorded
 * divergence (see `geo/globeFrame.ts`). The limb rim (`outline`) and the
 * atmosphere haze (`primary`) are the two things the app *also* draws in Compose
 * rather than on the GPU, and they are faithful.
 *
 * The element fills whatever box its parent gives it; pass the box's `aspect` so
 * the projection and the paint agree.
 */
export function GlobeView({ camera, aspect, scene, overlay, className, style }: GlobeViewProps) {
  const vbHeight = GLOBE_VB_HEIGHT
  const vbWidth = Math.round(vbHeight * aspect)
  const viewport: GlobeViewport = { width: vbWidth, height: vbHeight }
  const clipId = useId()

  const land = projectOutline(worldOutline(), camera, viewport)
  const limb = camera.limbCircle(viewport)

  return (
    <div className={['fp-globe-view', className].filter(Boolean).join(' ')} style={style}>
      <svg
        className="fp-globe-view__canvas"
        viewBox={`0 0 ${vbWidth} ${vbHeight}`}
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <defs>
          <clipPath id={clipId}>
            <circle cx={limb.cx} cy={limb.cy} r={limb.r} />
          </clipPath>
        </defs>

        {/* The backdrop: the theme's day sky at its high end — the thin air a
            planet seen from outside its atmosphere actually sits in. `backdrop`
            in GlobeInkTheme.kt. */}
        <rect x="0" y="0" width={vbWidth} height={vbHeight} fill="var(--fp-sky-day-high)" />
        {/* The sphere's own face, so the outline reads as land on a body. */}
        <circle cx={limb.cx} cy={limb.cy} r={limb.r} fill="var(--fp-surface)" />

        <g clipPath={`url(#${clipId})`}>
          {land.fill !== '' && (
            <path d={land.fill} fill="currentColor" fillOpacity={LAND_ALPHA} fillRule="evenodd" />
          )}
          {land.coast !== '' && (
            <path
              d={land.coast}
              fill="none"
              stroke="currentColor"
              strokeOpacity={COAST_ALPHA}
              strokeWidth={2}
              strokeLinejoin="bevel"
              strokeLinecap="butt"
            />
          )}
          {scene}
        </g>

        {/* The haze along the limb: `primary`, faint and wide. Then the rim
            itself: `outline`, a hairline. Both over the surface, so the colours
            are read where every other themed colour in this app is. */}
        <circle
          cx={limb.cx}
          cy={limb.cy}
          r={limb.r}
          fill="none"
          stroke="var(--fp-primary)"
          strokeOpacity={ATMOSPHERE_ALPHA}
          strokeWidth={10}
        />
        <circle
          cx={limb.cx}
          cy={limb.cy}
          r={limb.r}
          fill="none"
          stroke="var(--fp-outline)"
          strokeWidth={2}
        />
      </svg>
      {overlay != null && <div className="fp-globe-view__overlay">{overlay}</div>}
    </div>
  )
}
