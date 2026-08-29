import { PhoneFrame, TopAppBar } from '../components/AppChrome'

export type CheckStatus = 'pass' | 'warn' | 'fail' | 'running'

export interface StartupCheck {
  name: string
  status: CheckStatus
  /** What the check found, or why it could not run. */
  detail?: string
}

export interface StartupCheckScreenProps {
  checks: StartupCheck[]
  /** The build the checks ran against. */
  version?: string
  className?: string
}

const statusGlyph: Record<CheckStatus, string> = {
  pass: '✓',
  warn: '!',
  fail: '✗',
  running: '…',
}

/**
 * The self-check screen, reachable from Settings.
 *
 * A developer-facing surface rather than a user-facing one: it states what the app
 * verified about its own bundled data at startup — the airport index, the world
 * outline, the dataset version — so a broken asset is a visible failed check
 * rather than a screen that renders wrong somewhere else.
 *
 * **The status is a glyph and a colour and a word**, not a colour alone. A check
 * list read at a glance is exactly where a colour-only signal fails.
 */
export function StartupCheckScreen({ checks, version, className }: StartupCheckScreenProps) {
  const failing = checks.filter((c) => c.status === 'fail').length
  const warning = checks.filter((c) => c.status === 'warn').length
  // A warning gets its own headline rather than being folded into "all passed".
  // A green summary sitting directly above a row marked WARN is the headline
  // contradicting the list under it, which is worse than no headline.
  const summary =
    failing > 0
      ? `${failing} check${failing === 1 ? '' : 's'} failed`
      : checks.some((c) => c.status === 'running')
        ? 'Running…'
        : warning > 0
          ? `${warning} check${warning === 1 ? '' : 's'} with warnings`
          : 'All checks passed'

  return (
    <PhoneFrame className={className}>
      <div className="fp-screen fp-content-cap">
        <div className="fp-screen__header">
          <h1 className="fp-screen__title fp-type-headline-medium">Flight Planner</h1>
        </div>
        <div className="fp-screen__list">
          <div>
            <span
              className={[
                'fp-check__summary',
                'fp-type-title-medium',
                failing > 0 ? 'fp-check--fail' : warning > 0 ? 'fp-check--warn' : 'fp-check--pass',
              ].join(' ')}
            >
              {summary}
            </span>
            {version != null && (
              <div className="fp-screen__row-detail fp-type-label-small">{version}</div>
            )}
          </div>

          {checks.map((check) => (
            <div className="fp-check" key={check.name}>
              <span
                className={`fp-check__glyph fp-check--${check.status} fp-type-title-medium`}
                aria-hidden="true"
              >
                {statusGlyph[check.status]}
              </span>
              <div className="fp-screen__row-main">
                <span className="fp-screen__row-title fp-type-title-small">
                  {check.name}
                  <span className="fp-check__word fp-type-label-small"> · {check.status}</span>
                </span>
                {check.detail != null && (
                  <span className="fp-screen__row-detail fp-type-label-small">{check.detail}</span>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
    </PhoneFrame>
  )
}

export interface Licence {
  /** The dependency's name. */
  name: string
  /** `Apache-2.0`, `MIT`. */
  licence: string
  /** The copyright line, when the licence carries one. */
  copyright?: string
}

export interface LicencesScreenProps {
  licences: Licence[]
  className?: string
}

/**
 * The open-source licences, reachable from Settings.
 *
 * A legal obligation rather than a design opportunity: the job is that every
 * bundled dependency is named with its licence, legibly, and that the list is
 * scannable. Do not decorate it.
 */
export function LicencesScreen({ licences, className }: LicencesScreenProps) {
  return (
    <PhoneFrame className={className}>
      <TopAppBar title="Licences" onBack={() => {}} />
      <div className="fp-screen fp-content-cap">
        <div className="fp-screen__list">
          {licences.map((licence) => (
            <div className="fp-licence" key={licence.name}>
              <span className="fp-screen__row-title fp-type-title-small">{licence.name}</span>
              <span className="fp-screen__row-detail fp-type-label-small">{licence.licence}</span>
              {licence.copyright != null && (
                <span className="fp-screen__row-detail fp-type-body-small">
                  {licence.copyright}
                </span>
              )}
            </div>
          ))}
        </div>
      </div>
    </PhoneFrame>
  )
}
