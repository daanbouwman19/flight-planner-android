import { StartupCheckScreen } from '@flightplanner/design-mirror'

/**
 * Everything the app verified about its own bundled data at startup.
 *
 * **The status is a glyph and a colour and a word**, never a colour alone. A list
 * read at a glance is exactly where a colour-only signal fails, and this one is
 * read at a glance by definition.
 */
export const AllPassing = () => (
  <StartupCheckScreen
    version="1.4.0 · dataset 2026-08-14"
    checks={[
      { name: 'Airport index', status: 'pass', detail: '78,412 airports loaded in 41 ms' },
      { name: 'World outline', status: 'pass', detail: '122 rings, 4,601 points' },
      { name: 'Runway table', status: 'pass', detail: '42,196 runways' },
      { name: 'User database', status: 'pass', detail: 'schema 7' },
      { name: 'Weather cache', status: 'pass', detail: '12 reports, oldest 2 h' },
    ]}
  />
)

/**
 * A broken asset, which is what the screen exists for.
 *
 * A missing index is a *visible failed check* here rather than a screen that
 * renders wrong somewhere else — which is the whole argument for having this
 * surface at all.
 */
export const OneFailing = () => (
  <StartupCheckScreen
    version="1.4.0 · dataset 2026-08-14"
    checks={[
      { name: 'Airport index', status: 'fail', detail: 'Asset missing: maps/airports.idx' },
      { name: 'World outline', status: 'pass', detail: '122 rings, 4,601 points' },
      { name: 'Runway table', status: 'warn', detail: 'No thresholds for 118 fields' },
      { name: 'User database', status: 'pass', detail: 'schema 7' },
      { name: 'Weather cache', status: 'running' },
    ]}
  />
)

/** Mid-run, before anything has settled. */
export const Running = () => (
  <StartupCheckScreen
    checks={[
      { name: 'Airport index', status: 'pass', detail: '78,412 airports loaded in 41 ms' },
      { name: 'World outline', status: 'running' },
      { name: 'Runway table', status: 'running' },
      { name: 'User database', status: 'running' },
    ]}
  />
)
