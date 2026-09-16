package com.github.daanbouwman.flightplanner.launch

import androidx.lifecycle.ViewModel
import com.github.daanbouwman.flightplanner.core.database.repository.LogbookRepository
import com.github.daanbouwman.flightplanner.model.FlightRecord
import com.github.daanbouwman.flightplanner.navigation.Destination
import com.github.daanbouwman.flightplanner.ui.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Holds the [LaunchRequest] an Intent carried until the NavHost can act on it.
 *
 * Why a ViewModel scoped to the Activity, and not a `@Singleton`: the request
 * belongs to *this* activity's Intents. It has to outlive a rotation — a
 * shortcut tapped with the phone turning would otherwise be lost between
 * `onCreate` and the first composition — and it has to die with the activity,
 * so nothing lingers in the process for the widget's receiver to trip over.
 * A ViewModel is exactly that lifetime.
 *
 * Why the Activity offers and the NavHost consumes: the Intent is known in
 * `onCreate` and `onNewIntent`, and the graph that can navigate on it is
 * composed some frames later. Holding the value in between is the whole job.
 *
 * The primary constructor takes the two lookups [LaunchRequest.LastRoute]
 * needs as functions and is `internal`, so `LaunchViewModelTest` can hand it
 * fakes without Room or DataStore; the `@Inject` secondary maps the graph's
 * types onto them, the same shape `StartupCheckViewModel` has.
 */
@HiltViewModel
class LaunchViewModel internal constructor(
    private val readLastRoute: suspend () -> Destination.RouteDetail?,
    private val latestFlight: suspend () -> FlightRecord?,
) : ViewModel(), LaunchRequests {

    @Inject
    constructor(
        lastRouteStore: LastRouteStore,
        logbookRepository: LogbookRepository,
    ) : this(
        readLastRoute = lastRouteStore::read,
        latestFlight = { logbookRepository.page(limit = 1, offset = 0).firstOrNull() },
    )

    private val _pending = MutableStateFlow<LaunchRequest?>(null)
    override val pending: StateFlow<LaunchRequest?> = _pending.asStateFlow()

    /** Called by the Activity with whatever [LaunchIntents.parse] made of an Intent. */
    fun offer(request: LaunchRequest?) {
        if (request != null) _pending.value = request
    }

    override fun consume(request: LaunchRequest) {
        _pending.compareAndSet(request, null)
    }

    /**
     * The last route the user *opened*, then the newest logbook flight, then
     * nothing.
     *
     * "Last route" was never defined by the plan. The thing a user last looked
     * at is the reading that matches the words on the shortcut; a route from
     * the logbook is one they have already flown, so it is the fallback — for
     * a fresh install or a restore, where nothing has been opened yet — and it
     * arrives marked [Destination.RouteDetail.alreadyFlown] so the detail
     * screen does not offer to log it twice. Both reads are guarded: a
     * shortcut must land somewhere even if a store is unreadable.
     */
    override suspend fun resolveLastRoute(): Destination.RouteDetail? {
        runCatchingCancellable { readLastRoute() }.getOrNull()?.let { return it }
        val flight = runCatchingCancellable { latestFlight() }.getOrNull() ?: return null
        return Destination.RouteDetail(
            departureIcao = flight.departureIcao,
            destinationIcao = flight.arrivalIcao,
            aircraftId = flight.aircraftId,
            distanceNm = flight.distanceNm ?: 0,
            alreadyFlown = true,
        )
    }
}
