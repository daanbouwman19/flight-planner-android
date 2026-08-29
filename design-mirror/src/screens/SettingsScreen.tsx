import type { ReactNode } from 'react'
import { PhoneFrame, TopAppBar } from '../components/AppChrome'
import { ModeSelector } from '../components/ModeSelector'

export interface SettingsScreenProps {
  /** Index into Follow system / Light / Dark / Cockpit / Chart. */
  theme?: number
  dynamicColour?: boolean
  /** Index into Nautical / Metric / Imperial. */
  units?: number
  icaoOnly?: boolean
  /** Index into NOAA / AVWX. */
  weatherProvider?: number
  /** `OurAirports, 2026-08-14`. */
  datasetSnapshot?: string
  className?: string
}

/**
 * Settings, in the app's five sections: Appearance, Units, Airports, Weather, About.
 *
 * **Cockpit and Chart are not a third dark mode and a second light one.** Cockpit
 * is a near-black instrument panel for flying at night and Chart is printed chart
 * paper, and both deliberately ignore dynamic colour — each one's identity is a
 * specific pair of surfaces and ink, which a wallpaper-derived scheme cannot
 * promise. That is why the dynamic-colour switch reads as unavailable under them.
 */
export function SettingsScreen({
  theme = 0,
  dynamicColour = true,
  units = 0,
  icaoOnly = false,
  weatherProvider = 0,
  datasetSnapshot = 'OurAirports · 2026-08-14',
  className,
}: SettingsScreenProps) {
  const themeOverridesDynamic = theme === 3 || theme === 4
  return (
    <PhoneFrame className={className}>
      <TopAppBar title="Settings" onBack={() => {}} />
      <div className="fp-screen fp-content-cap">
        <div className="fp-screen__list">
          <Section title="Appearance">
            <ModeSelector
              options={[
                { label: 'System' },
                { label: 'Light' },
                { label: 'Dark' },
                { label: 'Cockpit' },
                { label: 'Chart' },
              ]}
              selectedIndex={theme}
            />
            <Toggle
              title="Dynamic colour"
              detail={
                themeOverridesDynamic
                  ? 'Cockpit and Chart use their own palette'
                  : 'Take the palette from the wallpaper'
              }
              on={dynamicColour && !themeOverridesDynamic}
            />
          </Section>

          <Section title="Units">
            <ModeSelector
              options={[{ label: 'Nautical' }, { label: 'Metric' }, { label: 'Imperial' }]}
              selectedIndex={units}
            />
          </Section>

          <Section title="Airports">
            <Toggle
              title="ICAO codes only"
              detail="Hide fields that publish no ICAO identifier"
              on={icaoOnly}
            />
          </Section>

          <Section title="Weather">
            <ModeSelector
              options={[{ label: 'NOAA' }, { label: 'AVWX' }]}
              selectedIndex={weatherProvider}
            />
            <span className="fp-screen__row-detail fp-type-label-small">
              NOAA needs no key. AVWX needs one, and covers more stations.
            </span>
          </Section>

          <Section title="About">
            <div className="fp-screen__setting">
              <div className="fp-screen__setting-main">
                <span className="fp-screen__row-title fp-type-body-large">Airport data</span>
                <span className="fp-screen__row-detail fp-type-label-small">{datasetSnapshot}</span>
              </div>
            </div>
            <button type="button" className="fp-button fp-button--text fp-type-label-large">
              Open-source licences
            </button>
          </Section>
        </div>
      </div>
    </PhoneFrame>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="fp-screen__section">
      <span className="fp-screen__section-title fp-type-label-large">{title}</span>
      {children}
    </div>
  )
}

function Toggle({ title, detail, on }: { title: string; detail: string; on: boolean }) {
  return (
    <div className="fp-screen__setting">
      <div className="fp-screen__setting-main">
        <span className="fp-screen__row-title fp-type-body-large">{title}</span>
        <span className="fp-screen__row-detail fp-type-label-small">{detail}</span>
      </div>
      <span
        role="switch"
        aria-checked={on}
        aria-label={title}
        className={['fp-screen__switch', on ? 'fp-screen__switch--on' : null]
          .filter(Boolean)
          .join(' ')}
      />
    </div>
  )
}
