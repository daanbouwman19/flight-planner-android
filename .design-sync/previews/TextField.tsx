import { TextField } from '@flightplanner/design-mirror'

/** Empty, filled, and filled with a unit — the three everyday states. */
export const States = () => (
  <div style={{ display: 'flex', flexDirection: 'column', gap: 16, width: 320 }}>
    <TextField label="Manufacturer" placeholder="Cessna" />
    <TextField label="Variant" value="172S Skyhawk" />
    <TextField label="Range" value="640" suffix="NM" />
  </div>
)

/**
 * A figure that did not validate.
 *
 * The message replaces the supporting text rather than appearing under it, so the
 * field does not change height when it goes wrong — a form that reflows as you
 * leave each field is a form that moves the next one out from under your thumb.
 */
export const Invalid = () => (
  <div style={{ display: 'flex', flexDirection: 'column', gap: 16, width: 320 }}>
    <TextField label="Range" value="0" suffix="NM" error supportingText="Must be above zero" />
    <TextField label="Cruise" value="120" suffix="kt" supportingText="At 75% power" />
  </div>
)

/**
 * A field whose value is a figure.
 *
 * Set tabular and left to right: a range or a runway length reordered by the
 * bidirectional algorithm under an RTL locale would be a wrong number rather
 * than an untidy one.
 */
export const Figures = () => (
  <div style={{ display: 'flex', flexDirection: 'column', gap: 16, width: 320 }}>
    <TextField label="Range" value="1,150" suffix="NM" />
    <TextField label="Takeoff distance" value="1,685" suffix="ft" />
  </div>
)
