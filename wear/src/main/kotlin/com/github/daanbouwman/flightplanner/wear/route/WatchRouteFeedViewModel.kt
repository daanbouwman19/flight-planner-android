package com.github.daanbouwman.flightplanner.wear.route

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.wear.data.WearAirportData
import com.github.daanbouwman.flightplanner.wear.handoff.HandoffResult
import com.github.daanbouwman.flightplanner.wear.handoff.PhoneHandoff
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * The route feed's state, and the two things the wearer can do to it: swipe to
 * the next route, and tap to open one on the phone.
 *
 * The primary constructor takes its reads as functions and is `internal`, so
 * `WatchRouteFeedViewModelTest` drives it against an in-memory index with no
 * assets and no paired phone; the `@Inject` secondary maps the graph's types
 * onto them. That is the shape `:app`'s `DailyChallengeSource` and
 * `LaunchViewModel` both have.
 *
 * [leadWithChallenge] is how the tile's tap arrives: the activity's intent
 * extras reach a Hilt ViewModel's [SavedStateHandle] as its default arguments,
 * so [EXTRA_LEAD_WITH_CHALLENGE] needs no plumbing through `MainActivity`.
 */
@HiltViewModel
class WatchRouteFeedViewModel internal constructor(
    private val loadIndex: suspend () -> AirportIndex,
    private val loadFleet: suspend () -> List<AircraftSpec>,
    private val loadOutline: suspend () -> WorldOutline,
    private val handoff: suspend (WatchRouteCard) -> HandoffResult,
    private val generatorDispatcher: CoroutineDispatcher,
    private val leadWithChallenge: Boolean = false,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    @Inject
    constructor(
        data: WearAirportData,
        phone: PhoneHandoff,
        savedState: SavedStateHandle,
    ) : this(
        loadIndex = data::index,
        loadFleet = data::fleet,
        loadOutline = data::outline,
        handoff = { card -> phone.open(card.asHandoff()) },
        generatorDispatcher = Dispatchers.Default,
        leadWithChallenge = savedState.get<Boolean>(EXTRA_LEAD_WITH_CHALLENGE) ?: false,
    )

    private val _state = MutableStateFlow<WatchRouteFeedState>(WatchRouteFeedState.Loading)
    val state: StateFlow<WatchRouteFeedState> = _state.asStateFlow()

    /**
     * What to say about the last tap.
     *
     * A `SharedFlow` and not part of [state], because it is an event rather than
     * a condition: the same message twice in a row is two separate things
     * happening, and a state would collapse them into one. `DROP_OLDEST` on a
     * buffer of one, so a wearer tapping faster than the phone answers sees the
     * newest outcome rather than a queue draining behind them.
     */
    private val _handoffs = MutableSharedFlow<HandoffResult>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val handoffs: Flow<HandoffResult> = _handoffs.asSharedFlow()

    private var feed: WatchRouteFeed? = null

    /** The in-flight top-up, so that two settles in a row do not start two. */
    private var topUp: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val index = read { loadIndex() } ?: return fail()
        val fleet = read { loadFleet() } ?: return fail()
        val outline = read { loadOutline() } ?: return fail()
        if (fleet.isEmpty()) return fail()

        val source = WatchRouteFeed(index, fleet, generatorDispatcher)
        feed = source
        val batch = read { source.nextBatch() } ?: return fail()
        // The tile promised this route, so it is the first page. A challenge
        // that cannot be built leaves the ordinary feed rather than failing it:
        // the tile has already said so on its own face. A batch route that is
        // the challenge again is dropped, because the pager is keyed by route.
        val challenge = if (leadWithChallenge) read { source.challengeFor(today()) } else null
        val first = if (challenge == null) {
            batch
        } else {
            listOf(challenge) + batch.filterNot { it.key() == challenge.key() }
        }
        _state.value = if (first.isEmpty()) {
            WatchRouteFeedState.Unavailable
        } else {
            WatchRouteFeedState.Ready(first, outline)
        }
    }

    /**
     * Called as the wearer settles on a page, so the feed can stay ahead of them.
     *
     * The pager reports the page it has come to rest on rather than every frame
     * of the swipe: a top-up started mid-flick would be racing the wearer's
     * thumb for no benefit, since a batch is sub-millisecond work and four pages
     * of headroom is a long time at swiping speed.
     */
    fun onPageSettled(page: Int) {
        val ready = _state.value as? WatchRouteFeedState.Ready ?: return
        val source = feed ?: return
        if (page < ready.routes.size - WatchRouteFeed.PREFETCH_MARGIN) return
        if (topUp?.isActive == true) return

        topUp = viewModelScope.launch {
            val more = read { source.nextBatch() } ?: return@launch
            if (more.isEmpty()) return@launch
            // Re-read rather than closing over `ready`: the append has to land on
            // whatever the state is now, and nothing else writes a Ready state
            // that this could be stale against — but reading it here keeps that
            // true if something ever does.
            val current = _state.value as? WatchRouteFeedState.Ready ?: return@launch
            _state.value = current.copy(routes = current.routes + more)
        }
    }

    /**
     * Asks the phone to open [card].
     *
     * Deliberately fire-and-forget from the composable's point of view: the
     * answer arrives on [handoffs] rather than as a returned value, so a tap
     * never blocks the face and a wearer who swipes on before the phone answers
     * still gets told what happened.
     */
    fun openOnPhone(card: WatchRouteCard) {
        viewModelScope.launch {
            _handoffs.emit(read { handoff(card) } ?: HandoffResult.Failed)
        }
    }

    private fun fail() {
        _state.value = WatchRouteFeedState.Unavailable
    }

    /**
     * Runs [block], returning null if it threw.
     *
     * Every read here is guarded for the same reason `:app`'s widget sources
     * are: a watch face that crashes is a watch face the wearer sees the
     * launcher instead of, and the honest answer to an asset that will not read
     * is [WatchRouteFeedState.Unavailable] — which at least says so. Cancellation
     * is rethrown; it means the ViewModel is going away, not that a read failed.
     *
     * Logged as well as swallowed: a failure here empties the whole app, and a
     * silent one leaves nothing in a bug report but "it says there are no
     * routes".
     */
    private inline fun <T> read(block: () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        Log.w(TAG, "A route-feed read failed", failure)
        null
    }

    companion object {
        /**
         * Set by the tile's tap: open on today's challenge rather than on a
         * fresh batch. Public because `ChallengeTileService` writes it.
         */
        const val EXTRA_LEAD_WITH_CHALLENGE: String =
            "com.github.daanbouwman.flightplanner.wear.extra.LEAD_WITH_CHALLENGE"

        private const val TAG = "WatchRouteFeed"
    }
}
