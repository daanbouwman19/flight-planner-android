package com.github.daanbouwman.flightplanner.index

import android.util.Log
import com.github.daanbouwman.flightplanner.core.database.airport.AirportIndexLoader
import com.github.daanbouwman.flightplanner.core.database.airport.IndexLoadTiming
import com.github.daanbouwman.flightplanner.di.ApplicationScope
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** Where the one process-wide index build has got to. */
sealed interface IndexState {

    /** Nothing has asked for the index yet and the warm-up has not run. */
    data object Idle : IndexState

    /** The asset is being read and decoded. */
    data object Loading : IndexState

    data class Ready(val index: AirportIndex, val timing: IndexLoadTiming) : IndexState

    data class Failed(val cause: Throwable) : IndexState

    /**
     * True once the outcome is known either way. The splash screen waits on this
     * rather than on [Ready]: a failed load must show the app (with its error
     * states) instead of holding the splash forever.
     */
    val settled: Boolean get() = this is Ready || this is Failed
}

/**
 * Owns the single [AirportIndex] for the process.
 *
 * Three properties matter here and each one is deliberate:
 *
 * **Built exactly once.** The index is ~24,000 airports of primitive arrays and
 * every consumer — route generation, search, the globe — reads the same
 * instance. A second copy would be pure waste, so the build is an `async` whose
 * result is cached by the `Deferred` itself; concurrent callers of [get] all
 * await the same job.
 *
 * **Started lazily, warmed eagerly.** [CoroutineStart.LAZY] means constructing
 * the provider costs nothing, so Hilt can build it during `Application.onCreate`
 * without paying for the decode there. [warm] then starts it on a background
 * dispatcher, where it overlaps first-frame inflation rather than following it.
 *
 * **Never released.** There is no `clear()`. Rebuilding costs a file read plus a
 * dozen array copies but the memory is held anyway for the app's whole life, so
 * dropping it would only trade a guaranteed cost for a variable one.
 *
 * The source is the prebuilt asset via [AirportIndexLoader]. Rebuilding from
 * SQLite rows was measured at 646 ms against a 500 ms cold-start budget and was
 * deleted for that reason; it must not come back.
 */
@Singleton
class AirportIndexProvider @Inject constructor(
    private val loader: AirportIndexLoader,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<IndexState>(IndexState.Idle)

    /** Observable progress, for skeletons and error states. */
    val state: StateFlow<IndexState> = _state.asStateFlow()

    /**
     * The in-flight or completed build.
     *
     * An `AtomicReference` rather than a `val` because a `Deferred` caches its
     * failure forever: one transient I/O error reading the asset would otherwise
     * disable route generation, search and the globe for the entire life of the
     * process, with force-stopping the app as the only remedy. Since the whole
     * point of `IndexState.Failed` is to put a retry in front of the user, the
     * failed job has to be replaceable. [retry] swaps in a fresh one.
     *
     * The success path is unaffected: once a build completes, every caller of
     * [get] awaits that same instance and the index is still built exactly once.
     */
    private val current = AtomicReference(newBuild())

    private fun newBuild(): Deferred<AirportIndex> =
        scope.async(Dispatchers.Default, CoroutineStart.LAZY) {
            _state.value = IndexState.Loading
            try {
                val loaded = loader.load()
                _state.value = IndexState.Ready(loaded.index, loaded.timing)
                loaded.index
            } catch (cancellation: CancellationException) {
                // The scope outlives the app's screens, so this only happens at
                // process teardown. It is not a load failure and must not be
                // reported as one.
                _state.value = IndexState.Idle
                throw cancellation
            } catch (failure: Throwable) {
                // Logged as well as surfaced: a failure here disables most of the
                // app, and a silent one leaves nothing to diagnose from a bug
                // report but "the lists are empty".
                Log.e(TAG, "Airport index failed to load", failure)
                _state.value = IndexState.Failed(failure)
                throw failure
            }
        }

    /**
     * Starts the build without waiting for it. Safe to call more than once;
     * `Deferred.start()` is a no-op once the job is running.
     */
    fun warm() {
        current.get().start()
    }

    /** Suspends until the index is built, starting the build if it has not been. */
    suspend fun get(): AirportIndex = current.get().await()

    /**
     * Discards a failed build so the next [get] or [warm] tries again.
     *
     * A no-op unless the current state is [IndexState.Failed] — retrying a
     * succeeded or in-flight build would throw away a good index or duplicate work.
     * The compare-and-set makes two taps on a retry button safe.
     */
    fun retry() {
        if (_state.value !is IndexState.Failed) return
        val failed = current.get()
        if (!failed.isCompleted) return
        if (current.compareAndSet(failed, newBuild())) {
            _state.value = IndexState.Idle
            current.get().start()
        }
    }

    /**
     * The index if it is already in memory, else null — for callers that must not
     * suspend, such as the splash-screen keep-on-screen condition.
     */
    val readyOrNull: AirportIndex? get() = (_state.value as? IndexState.Ready)?.index

    /** True once the load has either succeeded or failed. */
    val isSettled: Boolean get() = _state.value.settled

    private companion object {
        const val TAG = "AirportIndexProvider"
    }
}
