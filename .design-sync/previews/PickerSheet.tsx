import { PhoneFrame, PickerSheet, ScrimOverlay } from '@flightplanner/design-mirror'

const results = [
  { code: 'EHAM', name: 'Amsterdam Airport Schiphol', detail: 'Amsterdam, Netherlands', rules: 'VFR' as const },
  { code: 'EHRD', name: 'Rotterdam The Hague Airport', detail: 'Rotterdam, Netherlands', rules: 'MVFR' as const },
  { code: 'EHEH', name: 'Eindhoven Airport', detail: 'Eindhoven, Netherlands', rules: 'VFR' as const },
  { code: 'EHGG', name: 'Groningen Airport Eelde', detail: 'Groningen, Netherlands', rules: 'IFR' as const },
]

/**
 * Picking a departure.
 *
 * The sheet takes almost the whole window on purpose: it exists to be typed into
 * and the results are the content, so a half-height sheet spends the screen on the
 * thing being covered rather than the thing being chosen.
 */
export const Departure = () => (
  <PhoneFrame>
    <ScrimOverlay>
      <PickerSheet target="departure" query="EH" results={results} />
    </ScrimOverlay>
  </PhoneFrame>
)

/**
 * The search widened because nothing matched nearby.
 *
 * The notice sits **above** the results rather than replacing them. A user who
 * typed three letters and got matches from another country should be told why
 * before they read the list, not left to work it out from the locations.
 */
export const ScopeWidened = () => (
  <PhoneFrame>
    <ScrimOverlay>
      <PickerSheet
        target="destination"
        query="LIRF"
        scopeNotice="No match within range — showing all airports"
        results={[
          { code: 'LIRF', name: 'Rome–Fiumicino', detail: 'Rome, Italy', rules: 'VFR' as const },
          { code: 'LIRA', name: 'Rome Ciampino', detail: 'Rome, Italy', rules: 'VFR' as const },
        ]}
      />
    </ScrimOverlay>
  </PhoneFrame>
)

/** Nothing typed yet, and a selection already in place to clear. */
export const Aircraft = () => (
  <PhoneFrame>
    <ScrimOverlay>
      <PickerSheet
        target="aircraft"
        hasSelection
        results={[
          { code: 'C172', name: 'Cessna 172S Skyhawk', detail: '640 NM · 1,685 ft' },
          { code: 'DA40', name: 'Diamond DA40 NG', detail: '940 NM · 1,180 ft' },
          { code: 'SR22', name: 'Cirrus SR22T', detail: '1,021 NM · 1,517 ft' },
        ]}
      />
    </ScrimOverlay>
  </PhoneFrame>
)
