import { MorphingLoadingIndicator } from '@flightplanner/design-mirror'

/**
 * The two forms, at rest and contained.
 *
 * The four shapes are the real Material 3 Expressive polygons — Circle, Cookie,
 * Clover, VerySunny — exported from the Android design system as path data rather
 * than redrawn.
 */
export const Both = () => (
  <div style={{ display: 'flex', gap: 24, alignItems: 'center' }}>
    <MorphingLoadingIndicator />
    <MorphingLoadingIndicator contained />
  </div>
)

/** Sizes, for a full-screen load against an inline one. */
export const Sizes = () => (
  <div style={{ display: 'flex', gap: 24, alignItems: 'center' }}>
    <MorphingLoadingIndicator size={24} />
    <MorphingLoadingIndicator size={48} />
    <MorphingLoadingIndicator size={72} />
  </div>
)
