import { useMemo } from 'react'
import { GlobeView, GLOBE_VB_HEIGHT } from './GlobeView'
import { GlobeCameraControls, GlobeAttribution } from './GlobeChrome'
import { sampleGeoArc } from '../geo/mapFrame'
import { framePoints, projectArc, projectPoint, type GlobeViewport } from '../geo/globeFrame'
import type { VisitedAirport, VisitedLeg } from './StatsCards'

export interface GlobeNetworkProps {
  airports: VisitedAirport[]
  legs?: VisitedLeg[]
  /** The band's width / height. Taller than the flat map — a sphere needs a box near square. */
  aspect?: number
  className?: string
}

/** A field visited once. Small, but never so small it disappears. `NodeMinSize`. */
const NODE_MIN = 6
/** The most-visited field in the set. `NodeMaxSize`. */
const NODE_MAX = 15
const LEG_STROKE = 4
const LEG_CASING = LEG_STROKE + 6

/**
 * The visited network on the sphere — Stats' Flat/Globe toggle in its Globe
 * state. Ported from `GlobeNetworkBand` + `GlobeNodes` in `:feature:globe`.
 *
 * The camera frames every visited point at once (`GlobeFit.framePoints`), so a
 * logbook confined to one region zooms in and one spanning hemispheres pulls
 * back to the whole planet — which is the entire argument for offering the
 * sphere: it is honest about *distance* where the flat map is honest about
 * *density*. Node radius goes as the **square root** of the visit count, so the
 * dot's area is proportional to the number it stands for.
 *
 * Nodes carry no labels — a hundred four-letter codes over a planet is a word
 * cloud, not a map — and are culled at the limb rather than faded, because a dot
 * is too small for a fade to read as anything but a rendering fault.
 */
export function GlobeNetwork({ airports, legs = [], aspect = 320 / 260, className }: GlobeNetworkProps) {
  const scene = useMemo(() => {
    const vbHeight = GLOBE_VB_HEIGHT
    const vbWidth = Math.round(vbHeight * aspect)
    const viewport: GlobeViewport = { width: vbWidth, height: vbHeight }

    if (airports.length === 0) {
      return { camera: framePoints([0], [0], viewport), legPaths: [] as string[], nodes: [] as Node[] }
    }

    const camera = framePoints(
      airports.map((a) => a.lat),
      airports.map((a) => a.lon),
      viewport,
    )

    const legPaths = legs
      .map((leg) => {
        const arc = sampleGeoArc(leg.from[0], leg.from[1], leg.to[0], leg.to[1], 64)
        return projectArc(arc.lats, arc.lons, camera, viewport)
      })
      .filter((d) => d !== '')

    const maxVisits = Math.max(1, ...airports.map((a) => a.visits ?? 1))
    const nodes: Node[] = []
    for (const a of airports) {
      const p = projectPoint(a.lat, a.lon, camera, viewport)
      if (!p || !p.visible) continue
      nodes.push({
        icao: a.icao,
        x: p.x,
        y: p.y,
        r: NODE_MIN + (NODE_MAX - NODE_MIN) * Math.sqrt((a.visits ?? 1) / maxVisits),
      })
    }

    return { camera, legPaths, nodes }
  }, [airports, legs, aspect])

  return (
    <GlobeView
      camera={scene.camera}
      aspect={aspect}
      className={className}
      scene={
        <>
          {scene.legPaths.map((d, i) => (
            <g key={i}>
              <path
                d={d}
                fill="none"
                stroke="var(--fp-surface-container)"
                strokeWidth={LEG_CASING}
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              <path
                d={d}
                fill="none"
                stroke="var(--fp-primary)"
                strokeOpacity={0.85}
                strokeWidth={LEG_STROKE}
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </g>
          ))}
          {scene.nodes.map((n) => (
            <g key={n.icao}>
              {/* Cased with the surface colour, the way the flat map cases its
                  markers, so a dot over land and one over ocean read alike. */}
              <circle cx={n.x} cy={n.y} r={n.r + 3} fill="var(--fp-surface-container)" />
              <circle cx={n.x} cy={n.y} r={n.r} fill="var(--fp-primary)" />
            </g>
          ))}
        </>
      }
      overlay={
        <>
          <div style={{ position: 'absolute', right: 12, bottom: 12 }}>
            <GlobeCameraControls />
          </div>
          <div style={{ position: 'absolute', left: 12, bottom: 12 }}>
            <GlobeAttribution />
          </div>
        </>
      }
    />
  )
}

interface Node {
  icao: string
  x: number
  y: number
  r: number
}
