import { GlobeAttribution } from '@flightplanner/design-mirror'

const onGlass = (node: React.ReactNode) => (
  <div style={{ display: 'inline-flex', padding: 20, borderRadius: 12, background: 'var(--fp-sky-day-high)' }}>
    {node}
  </div>
)

/**
 * The imagery credit, on the same glass plate as the camera stack and the label
 * plates. Drawn in every layout the globe appears in because the provider
 * requires it — and hidden from accessibility, because it is a licence notice
 * rather than something a screen-reader user is looking for. It also appears in
 * Settings · About, in reading order, for anyone who goes looking.
 */
export const Default = () => onGlass(<GlobeAttribution />)

/** The text is a prop, for a concept that swaps the tile provider. */
export const OtherProvider = () => onGlass(<GlobeAttribution text="Imagery © Esri, Maxar" />)
