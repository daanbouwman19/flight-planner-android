import { SkeletonCard } from '@flightplanner/design-mirror'

/** The loading stand-in for a list of route cards. */
export const LoadingList = () => (
  <div style={{ display: 'flex', flexDirection: 'column', gap: 12, width: 328 }}>
    <SkeletonCard />
    <SkeletonCard />
    <SkeletonCard />
  </div>
)
