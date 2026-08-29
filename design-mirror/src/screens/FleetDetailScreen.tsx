import { PhoneFrame, TopAppBar } from '../components/AppChrome'
import { FleetDetailPane, type Aircraft } from './FleetScreen'

export interface FleetDetailScreenProps {
  aircraft: Aircraft
  /** Already formatted — `290 kt`. */
  cruiseSpeed?: string
  className?: string
}

/**
 * One airframe, as its own screen.
 *
 * The phone form of what a tablet shows as {@link FleetDetailPane} beside the
 * list. It is the same component inside a frame rather than a second layout, so
 * the two cannot drift apart.
 */
export function FleetDetailScreen({ aircraft, cruiseSpeed, className }: FleetDetailScreenProps) {
  return (
    <PhoneFrame className={className}>
      <TopAppBar title={aircraft.variant} onBack={() => {}} />
      <FleetDetailPane aircraft={aircraft} cruiseSpeed={cruiseSpeed} showTitle={false} />
    </PhoneFrame>
  )
}
