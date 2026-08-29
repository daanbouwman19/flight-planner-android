/**
 * Flight category, derived from ceiling and visibility.
 *
 * `UNKNOWN` is a real answer rather than a missing one: a report that mentions no
 * sky at all could be hiding a 200 ft overcast, so it is never given the benefit
 * of the doubt.
 */
export type FlightRules = 'VFR' | 'MVFR' | 'IFR' | 'LIFR' | 'UNKNOWN'

/** The text printed on the chip. `UNKNOWN` prints `N/A`. */
export const flightRulesCode: Record<FlightRules, string> = {
  VFR: 'VFR',
  MVFR: 'MVFR',
  IFR: 'IFR',
  LIFR: 'LIFR',
  UNKNOWN: 'N/A',
}

/** The first line of each category's description, as the badge announces it. */
export const flightRulesDescription: Record<FlightRules, string> = {
  VFR: 'Visual Flight Rules',
  MVFR: 'Marginal Visual Flight Rules',
  IFR: 'Instrument Flight Rules',
  LIFR: 'Low Instrument Flight Rules',
  UNKNOWN: 'Flight category unknown',
}

export interface FlightRulesBadgeProps {
  /** The category to state. */
  rules: FlightRules
  className?: string
}

/**
 * The flight category of a station, as a chip.
 *
 * **Its colours are not Material roles and never respond to the theme's accent.**
 * VFR green, MVFR blue, IFR red and LIFR magenta are the colours every aviation
 * weather chart uses, and a pilot reads the colour before the letters. They live
 * in their own `--fp-<category>-container` / `--fp-<category>-on-container`
 * variables, and the only thing that varies is whether the light or the dark tone
 * mapping is in effect — Cockpit shares the dark set unchanged, because a night
 * theme may restyle the app but it may not restate the weather.
 *
 * The category is stated in text as well as in colour, because roughly one man in
 * twelve cannot tell this particular green from this particular red, and "IFR"
 * printed on the chip costs three characters.
 *
 * ```tsx
 * <FlightRulesBadge rules="MVFR" />
 * ```
 */
export function FlightRulesBadge({ rules, className }: FlightRulesBadgeProps) {
  const key = rules.toLowerCase()
  return (
    <span
      className={['fp-rules-badge', `fp-rules-badge--${key}`, 'fp-type-label-medium', className]
        .filter(Boolean)
        .join(' ')}
      title={flightRulesDescription[rules]}
      aria-label={flightRulesDescription[rules]}
    >
      {flightRulesCode[rules]}
    </span>
  )
}
