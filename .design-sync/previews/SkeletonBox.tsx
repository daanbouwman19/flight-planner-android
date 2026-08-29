import { SkeletonBox } from '@flightplanner/design-mirror'

/**
 * One shimmering bar, at the three sizes a route card uses.
 *
 * Skeletons beat a spinner when the content's shape is known, because they stop
 * the layout jumping — and are worse than one when you are guessing at the size.
 */
export const Bars = () => (
  <div style={{ display: 'flex', flexDirection: 'column', gap: 10, width: 328 }}>
    <SkeletonBox width="55%" height="20px" />
    <SkeletonBox width="80%" height="14px" />
    <SkeletonBox width="35%" height="14px" />
  </div>
)

/** A block, for a hero area whose size is known. */
export const Block = () => <SkeletonBox width="328px" height="180px" radius="large" />
