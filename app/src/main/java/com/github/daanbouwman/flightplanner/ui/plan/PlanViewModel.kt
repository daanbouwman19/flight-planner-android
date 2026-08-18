package com.github.daanbouwman.flightplanner.ui.plan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.daanbouwman.flightplanner.core.database.repository.AirportRepository
import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.core.database.repository.LogbookRepository
import com.github.daanbouwman.flightplanner.di.DefaultDispatcher
import com.github.daanbouwman.flightplanner.index.AirportIndexProvider
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.FlightRecord
import com.github.daanbouwman.flightplanner.routing.AircraftSearchItem
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.AirportSlotSearch
import com.github.daanbouwman.flightplanner.routing.DEFAULT_ROUTE_BATCH
import com.github.daanbouwman.flightplanner.routing.GeneratedRoute
import com.github.daanbouwman.flightplanner.routing.GreatCircle
import com.github.daanbouwman.flightplanner.routing.RouteArc
import com.github.daanbouwman.flightplanner.routing.RouteGenerator
import com.github.daanbouwman.flightplanner.routing.RouteMode
import com.github.daanbouwman.flightplanner.routing.RouteRequest
import com.github.daanbouwman.flightplanner.routing.SearchCandidate
import com.github.daanbouwman.flightplanner.routing.SearchQuery
import com.github.daanbouwman.flightplanner.routing.SearchScorer
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.world.WorldOutlineLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

private const val TAG = "PlanViewModel"

/**
 * Drives the Plan screen: what to generate, what was generated, and what
 * happens when a route is swiped away.
 *
 * ### The generation loop
 *
 * Every input that changes *what should be on screen* — the mode, the locked
 * departure, the chosen airframe, and an explicit generate — is folded into one
 * [Selection] value. A single collector runs [collectLatest] over it, which is
 * the cancellation primitive the whole design rests on: changing the selection
 * cancels the batch already in flight before starting the next one. The desktop
 * application solves the same problem with an `AtomicU64` generation counter
 * compared after the fact; structured concurrency does it by construction, and
 * cannot leak a stale batch into a fresh list because the coroutine that would
 * have delivered it no longer exists.
 *
 * Appending is *inside* that collector rather than beside it. A "load more" must
 * extend the current batch, never replace it, and it must die with the selection
 * that produced it — collecting [appendRequests] within the same coroutine gives
 * both for free, and means a stale append cannot arrive after a mode change and
 * silently mix airframes into a filtered list.
 *
 * ### What is not on the startup path
 *
 * Nothing here runs until the Plan screen composes. The first batch is then
 * generated immediately — see the note in `init` — but every part of it is off
 * the main thread and gated on the airport index, so it lands after the first
 * frame rather than in front of it.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class PlanViewModel @Inject constructor(
    private val indexProvider: AirportIndexProvider,
    private val fleetRepository: FleetRepository,
    private val logbookRepository: LogbookRepository,
    private val airportRepository: AirportRepository,
    private val worldOutlineLoader: WorldOutlineLoader,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /**
     * The inputs a batch is a function of.
     *
     * [generation] is what makes "generate again with the same settings" a
     * change: without it, tapping the FAB twice would emit an equal value and a
     * `StateFlow` would conflate it away. It starts at zero, which is the
     * encoding of "the user has not asked for anything yet".
     */
    private data class Selection(
        val mode: PlanMode = PlanMode.Any,
        val lockedDeparture: Airport? = null,
        val selectedAircraft: AircraftSpec? = null,
        val generation: Long = 0,
    )

    /** The prepared inputs for one selection, hoisted so appends and replacements reuse them. */
    private class Batch(
        val index: AirportIndex,
        val generator: RouteGenerator,
        val request: RouteRequest,
    )

    /** What an undo has to put back. */
    private class UndoLog(
        val row: RouteRow,
        val position: Int,
        val recordId: Long,
        val aircraftWasFlown: Boolean,
        val aircraftDateFlown: String?,
        /**
         * Which batch the row belonged to.
         *
         * The undo window is ten seconds, which is long enough to change the mode
         * in — and a row logged under "Any" must not be spliced into a batch
         * generated for "Not flown". Reversing the *database* is always right;
         * putting the row back is only right if it is still the same list.
         */
        val generation: Long,
    )

    private val selection = MutableStateFlow(Selection())

    /**
     * Extra-batch requests.
     *
     * A [MutableSharedFlow] with no replay, deliberately: a request made against
     * a selection that has since changed must evaporate rather than be delivered
     * to the new one. `DROP_OLDEST` over a single slot collapses the burst a fast
     * scroll produces into one append instead of queueing five.
     */
    private val appendRequests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val routes = MutableStateFlow<List<RouteRow>>(emptyList())
    private val status = MutableStateFlow<PlanStatus>(PlanStatus.Idle)

    /**
     * One-shot events, as a channel rather than as state.
     *
     * A snackbar is not a property of the screen — replaying "flight logged" on
     * every configuration change would show it again each time the device is
     * rotated, and there is no correct moment to clear a state flag from the UI.
     */
    private val _events = Channel<PlanEvent>(Channel.BUFFERED)
    val events: Flow<PlanEvent> = _events.receiveAsFlow()

    private val rowIds = AtomicLong()

    /** Set while a selection is live, so a single-route replacement can reuse it. */
    @Volatile
    private var batch: Batch? = null

    @Volatile
    private var undoLog: UndoLog? = null

    /**
     * The fleet, observed rather than fetched, so that marking an airframe flown
     * repaints the not-flown count and the picker without anyone re-querying.
     */
    private val fleet: StateFlow<List<AircraftSpec>> = fleetRepository.observeFleet()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val notFlownCount: StateFlow<Int> = fleetRepository.observeNotFlownCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    /**
     * The coastline every card draws on.
     *
     * Kept out of [PlanUiState] on purpose. It is the same 122 rings for the
     * whole session and it changes exactly once — from empty to loaded — so
     * folding it into the state would make every route batch, every status change
     * and every mode flip carry a copy of it. Collected separately, the screen
     * reads it once and hands the same instance to every row.
     *
     * `WhileSubscribed` means the read starts when the Plan screen composes, not
     * when the ViewModel is built, which keeps it off the startup path.
     */
    val worldOutline: StateFlow<WorldOutline> = flow { emit(worldOutlineLoader.load()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WorldOutline.Empty)

    val uiState: StateFlow<PlanUiState> =
        combine(selection, routes, status, notFlownCount) { selected, rows, phase, notFlown ->
            PlanUiState(
                mode = selected.mode,
                lockedDeparture = selected.lockedDeparture,
                selectedAircraft = selected.selectedAircraft,
                notFlownCount = notFlown,
                routes = rows,
                status = phase,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PlanUiState())

    init {
        viewModelScope.launch {
            selection.collectLatest { runSelection(it) }
        }
        viewModelScope.launch {
            // Ranked search needs the name index, and building it is a full-table
            // text read. Starting it here rather than on the first keystroke means
            // the picker is usually ready before it is opened; starting it from a
            // ViewModel rather than from Application.onCreate keeps it off the
            // cold-start path. The repository makes the call idempotent.
            runCatching { airportRepository.prepareNameIndex(indexProvider.get()) }
        }

        // Open with routes already on screen.
        //
        // This screen used to start empty and wait to be asked, on the reasoning
        // that generating unprompted spends the startup budget on work nobody
        // requested. In use that reasoning is wrong: the Plan screen is the
        // launch destination and its entire purpose is a list of routes, so an
        // empty one asks the user to press a button to get the thing they opened
        // the app for. The cost is bounded — the batch waits on the airport index
        // either way, and every part of it runs off the main thread — so it lands
        // shortly after the first frame rather than competing with it.
        //
        // The empty state is still reachable, and still says something true: it
        // now means a batch ran and the filters excluded everything.
        generate()
    }

    // ---------------------------------------------------------------- intents

    fun setMode(mode: PlanMode) {
        selection.update { current ->
            // Leaving "this aircraft" does not forget the choice: coming back to
            // the tab and finding the airframe still selected is what a user
            // expects, and re-picking it from 116 rows is not free.
            current.copy(mode = mode)
        }
    }

    fun setDeparture(airport: Airport?) {
        selection.update { it.copy(lockedDeparture = airport) }
    }

    fun setAircraft(aircraft: AircraftSpec?) {
        selection.update {
            it.copy(
                selectedAircraft = aircraft,
                // Choosing an airframe from the picker is an unambiguous request
                // to plan for it, so it also switches the mode. Clearing the
                // choice cannot leave the mode pointing at nothing.
                mode = if (aircraft != null) PlanMode.SelectedAircraft else PlanMode.Any,
            )
        }
    }

    /** Generates a fresh batch, replacing whatever is on screen. */
    fun generate() {
        // A failed index has to be discarded before retrying, or the retry cannot
        // work. `DefaultAirportIndexProvider` caches its build in a `Deferred`,
        // and a `Deferred` caches its *failure* permanently — so every later
        // `get()` rethrows the same exception and the error screen returns
        // instantly, for the life of the process. `retry()` swaps in a fresh
        // build; it is a no-op unless the current state is `Failed`, which is why
        // it is safe to call from the ordinary generate path as well.
        if ((status.value as? PlanStatus.Failed)?.reason == PlanFailure.IndexUnavailable) {
            indexProvider.retry()
        }
        selection.update { it.copy(generation = it.generation + 1) }
    }

    /** Appends another batch beneath the current one. Ignored while one is already in flight. */
    fun loadMore() {
        if (status.value != PlanStatus.Ready) return
        appendRequests.tryEmit(Unit)
    }

    // ----------------------------------------------------------- swipe actions

    /**
     * Logs the route as flown and removes it from the list.
     *
     * Both halves of "flown" are written: a row in the logbook, which is what
     * the Logbook and Stats screens read, and the airframe's own flag and date,
     * which is what the not-flown filter reads. The desktop application updates
     * both from its equivalent action, and updating only one produces a fleet
     * that disagrees with its own history.
     */
    fun markFlown(row: RouteRow) {
        viewModelScope.launch {
            val position = routes.value.indexOf(row)
            if (position < 0) return@launch
            val generation = selection.value.generation

            // Removed first, so the card leaves under the finger rather than after
            // a database round trip.
            routes.update { it - row }

            // Both writes in one guarded block. Unguarded they would escape into
            // `viewModelScope` and crash the process, and a failure between them
            // would leave the logbook and the airframe's flag disagreeing — the
            // exact state this method exists to keep in step.
            val logged = runCatchingCancellable {
                val previous = fleetRepository.byId(row.aircraft.id)
                val recordId = logbookRepository.add(
                    FlightRecord(
                        id = 0,
                        departureIcao = row.departure.icao,
                        arrivalIcao = row.destination.icao,
                        aircraftId = row.aircraft.id,
                        date = LocalDate.now().toString(),
                        distanceNm = row.distanceNm,
                    ),
                )
                fleetRepository.setFlown(row.aircraft.id, flown = true)
                UndoLog(
                    row = row,
                    position = position,
                    recordId = recordId,
                    aircraftWasFlown = previous?.flown ?: false,
                    aircraftDateFlown = previous?.dateFlown,
                    generation = generation,
                )
            }.getOrElse { failure ->
                // Nothing was logged, so the row must come back: a card that
                // vanished without being recorded anywhere is the one outcome the
                // user cannot detect or correct.
                Log.e(TAG, "Marking ${row.departure.icao} to ${row.destination.icao} flown failed", failure)
                restore(row, position, generation)
                return@launch
            }

            undoLog = logged
            _events.trySend(PlanEvent.FlightLogged(row.departure.icao, row.destination.icao))
        }
    }

    /**
     * Puts a row back where it was, but only if it still belongs there.
     *
     * A batch generated since the row left is a different list under different
     * filters, and re-inserting into it would mix an airframe into a set that
     * excludes it — the same leak `collectLatest` prevents for appends.
     */
    private fun restore(row: RouteRow, position: Int, generation: Long) {
        if (selection.value.generation != generation) return
        routes.update { current ->
            if (row in current) {
                current
            } else {
                current.toMutableList().apply { add(position.coerceIn(0, size), row) }
            }
        }
    }

    /**
     * Reverses the last [markFlown].
     *
     * The airframe is restored to the exact state it was in, date included —
     * an aircraft first flown in 2019 must not come back stamped with today.
     */
    fun undoMarkFlown() {
        val log = undoLog ?: return
        undoLog = null

        // The list comes back first, before any of the database work.
        //
        // An undo has to feel instant — it is the user retracting something, and a
        // retraction that waits on two writes reads as a second thing going wrong.
        // [restore] declines if the batch has changed since; the database is
        // reversed either way, because the flight was logged either way.
        restore(log.row, log.position, log.generation)

        viewModelScope.launch {
            runCatchingCancellable {
                logbookRepository.delete(
                    FlightRecord(
                        id = log.recordId,
                        departureIcao = log.row.departure.icao,
                        arrivalIcao = log.row.destination.icao,
                        aircraftId = log.row.aircraft.id,
                        date = LocalDate.now().toString(),
                        distanceNm = log.row.distanceNm,
                    ),
                )
                if (log.aircraftWasFlown) {
                    val on = runCatching { LocalDate.parse(log.aircraftDateFlown) }.getOrNull()
                    if (on != null) {
                        fleetRepository.setFlown(log.row.aircraft.id, flown = true, on = on)
                    } else {
                        fleetRepository.setFlown(log.row.aircraft.id, flown = true)
                    }
                } else {
                    fleetRepository.setFlown(log.row.aircraft.id, flown = false)
                }
            }.onFailure {
                // The row is already back on screen, so the user's intent has been
                // honoured visually; what is left is a logbook row that should not
                // be there, which the Logbook screen can delete.
                Log.e(TAG, "Undoing the log entry for ${log.row.departure.icao} failed", it)
            }
        }
    }

    /**
     * Discards one route and generates a new destination for the same flight.
     *
     * Replace is not "give me another route" — pull-to-refresh does that, for
     * the whole list. It is the answer to *"this leg, but somewhere else"*: the
     * airframe and the field it is standing at are the parts the user did not
     * ask to change, so they stay and only the far end moves. Re-rolling the
     * batch's own request instead would change all three, and swiping away a
     * 737 out of Schiphol could hand back a Cessna out of Anchorage.
     *
     * So the request is narrowed to this row — [RouteMode.Specific] for its
     * airframe, its departure locked — while everything else comes from the
     * prepared batch, which keeps the replacement generated under the same
     * filters as the neighbours it lands between.
     *
     * A handful of candidates are generated rather than one, because a locked
     * departure with little in range readily returns the destination just
     * discarded, and a replace that hands back the same card reads as a broken
     * swipe. The first candidate that is somewhere else wins; if they are all
     * the same place then that is the only answer there is, and showing it
     * beats leaving a hole. If generation produces nothing at all — an airframe
     * that can reach nowhere from where it stands — the row is simply gone.
     *
     * ### The row is swapped, not removed and re-added
     *
     * The old row stays in the list until the new one exists, and then both
     * happen in a single update. It is tempting to drop it first so the card
     * leaves "under the finger", but the card has already left: the swipe
     * carried it off screen, and what removing the row actually does is close
     * the *slot*. Closing it and reopening it a few milliseconds later drags
     * every card below up and back down for no reason anybody can see, and on a
     * locked departure with little in range those milliseconds are not few.
     * Held open, the gap is simply where the replacement appears.
     *
     * The position is therefore also resolved inside the update rather than
     * before the generation, so a batch that arrived meanwhile cannot be spliced
     * at a stale index.
     */
    fun replace(row: RouteRow) {
        viewModelScope.launch {
            if (row !in routes.value) return@launch

            val current = batch
            val candidates = if (current == null) {
                emptyList()
            } else {
                runCatchingCancellable {
                    buildRows(
                        current,
                        current.request.copy(
                            mode = RouteMode.Specific(row.aircraft.id),
                            lockedDepartureIcao = row.departure.icao,
                            amount = REPLACEMENT_CANDIDATES,
                        ),
                        arrivedAsReplacement = true,
                    )
                }.getOrNull().orEmpty()
            }

            val replacement = candidates.firstOrNull { it.destination.id != row.destination.id }
                ?: candidates.firstOrNull()

            routes.update { rows ->
                val position = rows.indexOf(row)
                if (position < 0) {
                    rows
                } else {
                    rows.toMutableList().apply {
                        if (replacement == null) removeAt(position) else set(position, replacement)
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------- generation

    private suspend fun runSelection(selected: Selection) {
        batch = null
        if (selected.generation == 0L) {
            routes.value = emptyList()
            status.value = PlanStatus.Idle
            return
        }
        if (selected.mode == PlanMode.SelectedAircraft && selected.selectedAircraft == null) {
            routes.value = emptyList()
            status.value = PlanStatus.Idle
            return
        }

        routes.value = emptyList()
        status.value = PlanStatus.Generating

        val prepared = prepare(selected)
        if (prepared == null) return
        batch = prepared

        val first = runCatchingCancellable { buildRows(prepared, prepared.request) }
            .onFailure { fail(it, PlanFailure.Unknown) }
            .getOrNull() ?: return

        routes.value = first
        status.value = PlanStatus.Ready

        appendRequests.collect {
            status.value = PlanStatus.Appending
            val more = runCatchingCancellable { buildRows(prepared, prepared.request) }
                .onFailure { Log.w(TAG, "Appending a batch failed; keeping what is shown", it) }
                .getOrNull()
                .orEmpty()
            routes.update { it + more }
            status.value = PlanStatus.Ready
        }
    }

    private suspend fun prepare(selected: Selection): Batch? {
        val index = runCatchingCancellable { indexProvider.get() }
            .onFailure { fail(it, PlanFailure.IndexUnavailable) }
            .getOrNull() ?: return null

        // Guarded like every other repository call here, and for a sharper reason
        // than tidiness: this runs inside the *single* collector that drives all
        // generation, so an exception escaping it kills that coroutine — no later
        // mode change, filter change or refresh would ever produce a batch again —
        // and with no handler on `viewModelScope` it takes the process with it.
        val airframes = runCatchingCancellable { fleetRepository.fleet() }
            .onFailure { fail(it, PlanFailure.Unknown) }
            .getOrNull() ?: return null

        if (airframes.isEmpty()) {
            status.value = PlanStatus.Failed(PlanFailure.FleetEmpty)
            return null
        }

        val mode = when (selected.mode) {
            PlanMode.Any -> RouteMode.AllAircraft
            PlanMode.NotFlown -> RouteMode.NotFlownOnly
            // Unreachable with a null airframe: runSelection returns early above.
            PlanMode.SelectedAircraft -> RouteMode.Specific(selected.selectedAircraft!!.id)
        }

        return Batch(
            index = index,
            generator = RouteGenerator(index, dispatcher = defaultDispatcher),
            request = RouteRequest(
                mode = mode,
                fleet = airframes,
                amount = DEFAULT_ROUTE_BATCH,
                lockedDepartureIcao = selected.lockedDeparture?.icao,
            ),
        )
    }

    /**
     * Turns generated routes into rows the card can draw without further work.
     *
     * The airport text is fetched by **id, in one query for the whole batch** —
     * the in-memory index carries no names, and asking per route would be a
     * hundred round trips for one screen. The slot-to-airport mapping is built
     * from the ids the caller already holds rather than by looking each airport
     * back up by its code, which would be a binary search per row to recover
     * something never lost.
     */
    private suspend fun buildRows(
        current: Batch,
        request: RouteRequest,
        arrivedAsReplacement: Boolean = false,
    ): List<RouteRow> {
        val generated = current.generator.generate(request)
        if (generated.isEmpty()) return emptyList()

        val slots = LinkedHashSet<Int>(generated.size * 2)
        for (route in generated) {
            slots += route.departureSlot
            slots += route.destinationSlot
        }
        val slotList = slots.toIntArray()
        val ids = slotList.map { current.index.ids[it] }
        val airportsById = airportRepository.airportsByIdMap(ids)

        val bySlot = HashMap<Int, Airport>(slotList.size)
        slotList.forEachIndexed { position, slot ->
            airportsById[ids[position]]?.let { bySlot[slot] = it }
        }

        return withContext(defaultDispatcher) {
            generated.mapNotNull { route -> toRow(route, bySlot, arrivedAsReplacement) }
        }
    }

    private fun toRow(
        route: GeneratedRoute,
        bySlot: Map<Int, Airport>,
        arrivedAsReplacement: Boolean,
    ): RouteRow? {
        // A missing display row means the index and the database disagree, which
        // is a dataset defect rather than a user-facing one. Dropping the route
        // is better than showing a card with a blank airport on it.
        val departure = bySlot[route.departureSlot] ?: return null
        val destination = bySlot[route.destinationSlot] ?: return null
        return RouteRow(
            id = rowIds.incrementAndGet(),
            aircraft = route.aircraft,
            departure = departure,
            destination = destination,
            distanceNm = route.distanceNm,
            flightTime = GreatCircle.flightTime(
                distanceNm = route.distanceNm.toDouble(),
                cruiseSpeedKt = route.aircraft.cruiseSpeedKt,
            ),
            departureRunwayFt = route.departureRunwayFt,
            destinationRunwayFt = route.destinationRunwayFt,
            arc = RouteArc.sampleGeographic(
                depLat = departure.latitude,
                depLon = departure.longitude,
                destLat = destination.latitude,
                destLon = destination.longitude,
                samples = RouteArc.CARD_SAMPLES,
            ),
            arrivedAsReplacement = arrivedAsReplacement,
        )
    }

    private fun fail(cause: Throwable, reason: PlanFailure) {
        Log.e(TAG, "Route generation failed ($reason)", cause)
        status.value = PlanStatus.Failed(reason)
    }

    // ------------------------------------------------------------------ search

    private val airportQuery = MutableStateFlow("")
    private val aircraftQuery = MutableStateFlow("")

    /**
     * Ranked airports for the departure picker.
     *
     * Debounced because the scan runs over the whole index and a fast typist
     * outruns it otherwise; [mapLatest] because a superseded query's results are
     * worthless, so the scan for it is cancelled rather than awaited.
     */
    val airportResults: StateFlow<List<Airport>> = airportQuery
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MILLIS }
        .mapLatest { searchAirports(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val aircraftResults: StateFlow<List<AircraftSpec>> =
        combine(aircraftQuery, fleet) { query, airframes -> query to airframes }
            .mapLatest { (query, airframes) -> searchAircraft(query, airframes) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun setAirportQuery(query: String) {
        airportQuery.value = query
    }

    fun setAircraftQuery(query: String) {
        aircraftQuery.value = query
    }

    private suspend fun searchAirports(query: String): List<Airport> {
        val index = runCatchingCancellable { indexProvider.get() }.getOrNull() ?: return emptyList()
        val slots = if (query.isBlank()) {
            suggestionSlots(index)
        } else {
            // Null when the name index has not finished building, which degrades
            // search to codes only rather than blocking a keystroke on a
            // full-table read — see AirportRepository.prepareNameIndex.
            val names = airportRepository.nameIndexOrNull()
            withContext(defaultDispatcher) {
                AirportSlotSearch.rank(
                    query = query,
                    codes = index.codes,
                    size = index.size,
                    names = names?.names,
                    municipalities = names?.municipalities,
                )
            }
        }
        return runCatchingCancellable { airportRepository.airportsForSlots(index, slots) }
            .getOrDefault(emptyList())
    }

    /**
     * What to show before anything has been typed: the largest airports.
     *
     * The index is sorted ascending by runway length, so the longest runways are
     * the last slots — which is why this walks backwards rather than taking the
     * head. Runway length is a good proxy for "an airport you have heard of",
     * and it costs nothing because the ordering already exists.
     */
    private fun suggestionSlots(index: AirportIndex): IntArray {
        val count = minOf(AirportSlotSearch.DEFAULT_LIMIT, index.size)
        return IntArray(count) { index.size - 1 - it }
    }

    private suspend fun searchAircraft(query: String, airframes: List<AircraftSpec>): List<AircraftSpec> =
        withContext(defaultDispatcher) {
            if (query.isBlank()) return@withContext airframes
            // The row carries its own spec, so a ranked result is the airframe
            // itself. Matching back by type code would be wrong as well as slow:
            // an ICAO *type* code is shared by every variant of a model, so two
            // A320s in the fleet are indistinguishable by it.
            SearchScorer.rank(airframes.map(::FleetRow), query).map { it.spec }
        }

    /** Adapts an airframe to the shared scorer without copying it. */
    private class FleetRow(val spec: AircraftSpec) : SearchCandidate {
        private val item = AircraftSearchItem.from(spec)

        override fun searchScore(query: SearchQuery): Int = item.searchScore(query)
    }

    private companion object {
        /**
         * How long a flow keeps running after the last collector goes away.
         * Long enough to survive a configuration change, short enough that a
         * backgrounded app stops observing the database.
         */
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * Typing pause before a search runs. Below roughly this the scan is
         * being restarted more often than it can finish on a mid-range device;
         * above it, the list visibly lags the keyboard.
         */
        const val SEARCH_DEBOUNCE_MILLIS = 120L

        /**
         * How many destinations [replace] generates to choose one from. Small,
         * because the only thing the spares buy is a way past the destination
         * just discarded, and a route is a binary search plus a bounded scan —
         * eight of them do not cost a frame.
         */
        const val REPLACEMENT_CANDIDATES = 8
    }
}

/**
 * [runCatching] that lets cancellation through.
 *
 * `runCatching` swallows `CancellationException` along with everything else,
 * which quietly breaks structured concurrency: a cancelled generation would be
 * reported to the user as a failure, and the coroutine would carry on running
 * code after the point it was supposed to stop.
 */
private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}
