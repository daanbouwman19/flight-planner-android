import { BottomSheet, PhoneFrame, ScrimOverlay, TextField } from '@flightplanner/design-mirror'

/**
 * A short form sheet, sized to its own content.
 *
 * `auto` rather than a height fraction: a sheet with three fields in it that
 * claimed 90% of the window would be mostly empty, and the emptiness would read
 * as something missing.
 */
export const AutoHeight = () => (
  <div style={{ width: 380, background: 'var(--fp-surface-dim)', padding: 16 }}>
    <BottomSheet title="Add aircraft" auto>
      <TextField label="Manufacturer" value="Cessna" />
      <TextField label="Variant" value="172S Skyhawk" />
    </BottomSheet>
  </div>
)

/**
 * The tall form — the picker's proportion, on a phone, over a scrim.
 *
 * This is the arrangement to design against when the concept is about the modal
 * state itself rather than about the sheet's contents.
 */
export const OverAScreen = () => (
  <PhoneFrame>
    <ScrimOverlay>
      <BottomSheet title="Departure airport" heightFraction={0.9}>
        <TextField label="Search" value="AMS" />
      </BottomSheet>
    </ScrimOverlay>
  </PhoneFrame>
)
