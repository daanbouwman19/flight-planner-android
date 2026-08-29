import { BottomSheet, TextField } from './BottomSheet'
import { FlightRulesBadge, type FlightRules } from './FlightRulesBadge'

export interface PickerResult {
  /** `EHAM`, or an aircraft's short code. */
  code: string
  /** The full name — `Amsterdam Airport Schiphol`. */
  name: string
  /** One line of context: a location, or an aircraft's envelope. */
  detail?: string
  rules?: FlightRules
}

export interface PickerSheetProps {
  /** What is being picked. */
  target: 'departure' | 'destination' | 'aircraft'
  /** What has been typed. */
  query?: string
  results: PickerResult[]
  /** Draws a "clear" action, for a filter that is currently set. */
  hasSelection?: boolean
  /** Shown above the results when the search fell back to a wider scope. */
  scopeNotice?: string
  className?: string
}

const pickerTitles = {
  departure: 'Departure airport',
  destination: 'Destination airport',
  aircraft: 'Aircraft',
} as const

/**
 * The search-and-pick sheet behind Plan's filter fields.
 *
 * **It takes almost the whole window on purpose.** The sheet exists to be typed
 * into and the results are the content, so a half-height sheet would spend the
 * screen on the thing being covered rather than on the thing being chosen. In the
 * app it also takes focus one frame after composing, so the keyboard rises *with*
 * the sheet rather than after it — a version that waited for the sheet to settle
 * first measured 1.55 s of dead beat.
 */
export function PickerSheet({
  target,
  query = '',
  results,
  hasSelection = false,
  scopeNotice,
  className,
}: PickerSheetProps) {
  return (
    <BottomSheet title={pickerTitles[target]} className={className}>
      <TextField
        label="Search"
        value={query}
        placeholder={target === 'aircraft' ? 'Type a variant' : 'Type a code or a name'}
      />

      {hasSelection && (
        <button type="button" className="fp-button fp-button--text fp-type-label-large" style={{ alignSelf: 'flex-start' }}>
          Clear selection
        </button>
      )}

      {scopeNotice != null && (
        <span className="fp-screen__row-detail fp-type-label-small">{scopeNotice}</span>
      )}

      <div>
        {results.map((result) => (
          <button type="button" className="fp-sheet__result" key={result.code}>
            <div className="fp-sheet__result-main">
              <span className="fp-screen__row-title fp-type-title-medium">
                <span className="fp-screen__row-figure">{result.code}</span> {result.name}
              </span>
              {result.detail != null && (
                <span className="fp-screen__row-detail fp-type-label-small">{result.detail}</span>
              )}
            </div>
            {result.rules != null && <FlightRulesBadge rules={result.rules} />}
          </button>
        ))}
      </div>
    </BottomSheet>
  )
}

export interface AddAircraftSheetProps {
  manufacturer?: string
  variant?: string
  icaoCode?: string
  category?: string
  /** Already formatted — `640`. The unit is the field's suffix. */
  range?: string
  cruise?: string
  takeoff?: string
  /** Field name → message, for a submitted form that did not validate. */
  errors?: Partial<Record<'manufacturer' | 'variant' | 'range' | 'cruise' | 'takeoff', string>>
  className?: string
}

/**
 * Adding an airframe to the fleet.
 *
 * The envelope fields — range, cruise, takeoff — are what every generated route is
 * constrained by, so they carry their units as suffixes rather than in the label:
 * a reader entering 640 needs to know it is nautical miles at the moment they type
 * it, not from a caption above.
 */
export function AddAircraftSheet({
  manufacturer,
  variant,
  icaoCode,
  category,
  range,
  cruise,
  takeoff,
  errors = {},
  className,
}: AddAircraftSheetProps) {
  return (
    <BottomSheet title="Add aircraft" auto className={className}>
      <TextField
        label="Manufacturer"
        value={manufacturer}
        placeholder="Cessna"
        error={errors.manufacturer != null}
        supportingText={errors.manufacturer}
      />
      <TextField
        label="Variant"
        value={variant}
        placeholder="172S Skyhawk"
        error={errors.variant != null}
        supportingText={errors.variant}
      />
      <TextField label="ICAO type code" value={icaoCode} placeholder="C172" />
      <TextField label="Category" value={category} placeholder="Single Engine Piston" />
      <div className="fp-sheet__row">
        <TextField
          label="Range"
          value={range}
          suffix="NM"
          error={errors.range != null}
          supportingText={errors.range}
        />
        <TextField
          label="Cruise"
          value={cruise}
          suffix="kt"
          error={errors.cruise != null}
          supportingText={errors.cruise}
        />
      </div>
      <TextField
        label="Takeoff distance"
        value={takeoff}
        suffix="ft"
        error={errors.takeoff != null}
        supportingText={errors.takeoff}
      />
      <div className="fp-sheet__actions">
        <button type="button" className="fp-button fp-type-label-large">
          Add aircraft
        </button>
      </div>
    </BottomSheet>
  )
}

export interface EditEnvelopeSheetProps {
  /** Which airframe is being edited. */
  aircraft: string
  range?: string
  cruise?: string
  takeoff?: string
  className?: string
}

/**
 * Editing one airframe's envelope.
 *
 * Deliberately the three figures alone rather than the whole add form: these are
 * the numbers a user actually revises — a published range is a book value, and the
 * one they fly to is theirs.
 */
export function EditEnvelopeSheet({
  aircraft,
  range,
  cruise,
  takeoff,
  className,
}: EditEnvelopeSheetProps) {
  return (
    <BottomSheet title={aircraft} auto className={className}>
      <span className="fp-screen__row-detail fp-type-body-medium">
        The envelope every generated route for this airframe is checked against.
      </span>
      <div className="fp-sheet__row">
        <TextField label="Range" value={range} suffix="NM" />
        <TextField label="Cruise" value={cruise} suffix="kt" />
      </div>
      <TextField label="Takeoff distance" value={takeoff} suffix="ft" />
      <div className="fp-sheet__actions">
        <button type="button" className="fp-button fp-button--text fp-type-label-large">
          Cancel
        </button>
        <button type="button" className="fp-button fp-type-label-large">
          Save
        </button>
      </div>
    </BottomSheet>
  )
}

export interface AddFlightSheetProps {
  departure?: string
  destination?: string
  aircraft?: string
  /** `29 Aug 2026`. */
  date?: string
  duration?: string
  /** Field name → message. */
  errors?: Partial<Record<'departure' | 'destination' | 'aircraft' | 'duration', string>>
  className?: string
}

/**
 * Logging a flight that has been flown.
 *
 * The two ends are ICAO codes rather than a picker of every airport, because a
 * flight being logged has already happened and the user knows what they flew.
 */
export function AddFlightSheet({
  departure,
  destination,
  aircraft,
  date,
  duration,
  errors = {},
  className,
}: AddFlightSheetProps) {
  return (
    <BottomSheet title="Log a flight" auto className={className}>
      <div className="fp-sheet__row">
        <TextField
          label="From"
          value={departure}
          placeholder="EHAM"
          error={errors.departure != null}
          supportingText={errors.departure}
        />
        <TextField
          label="To"
          value={destination}
          placeholder="EGLL"
          error={errors.destination != null}
          supportingText={errors.destination}
        />
      </div>
      <TextField
        label="Aircraft"
        value={aircraft}
        placeholder="Cessna 172S Skyhawk"
        error={errors.aircraft != null}
        supportingText={errors.aircraft}
      />
      <div className="fp-sheet__row">
        <TextField label="Date" value={date} placeholder="29 Aug 2026" />
        <TextField
          label="Duration"
          value={duration}
          suffix="h:mm"
          error={errors.duration != null}
          supportingText={errors.duration}
        />
      </div>
      <div className="fp-sheet__actions">
        <button type="button" className="fp-button fp-button--text fp-type-label-large">
          Cancel
        </button>
        <button type="button" className="fp-button fp-type-label-large">
          Log flight
        </button>
      </div>
    </BottomSheet>
  )
}
