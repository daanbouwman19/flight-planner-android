package com.github.daanbouwman.flightplanner.wear.theme

import android.content.Context
import android.net.Uri
import android.util.Log
import com.github.daanbouwman.flightplanner.handoff.WatchThemeState
import com.github.daanbouwman.flightplanner.handoff.WatchThemeSync
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The phone's chosen look, as the watch sees it.
 *
 * Reads the `DataItem` the phone writes (see `PublishThemeToWatch` in `:app`,
 * and [WatchThemeSync] for the shape of it), then stays subscribed for changes,
 * so flipping the setting on the phone recolours the face without reopening it.
 *
 * **It works offline, and that is the point of using a `DataItem`.** The Data
 * Layer replicates the item to this watch and keeps it here, so the first read
 * below returns the last theme the phone published even with the phone off, out
 * of range or unpaired. Nothing is cached a second time on top of that: a local
 * copy would be a second answer to keep in sync with the first.
 *
 * Before the phone has ever published — a fresh install, or a watch that has
 * never been paired — the read finds nothing and the face takes
 * [WatchThemeSync.Unsynced]. Every failure here lands there too: a watch with no
 * Play Services shows the default look rather than failing to start.
 */
@Singleton
class WatchThemeSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val client: DataClient by lazy { Wearable.getDataClient(context) }

    /**
     * The item's address. Path-only, with no node in it, so it matches the copy
     * held on this watch whichever phone wrote it — a watch re-paired to a new
     * phone would otherwise keep watching an address nothing writes to.
     */
    private val uri: Uri = Uri.Builder()
        .scheme(PutDataRequest.WEAR_URI_SCHEME)
        .path(WatchThemeSync.PATH)
        .build()

    /**
     * What is stored now, then every change to it.
     *
     * [distinctUntilChanged] because the Data Layer re-delivers an item whose
     * contents did not change — a re-sync after reconnecting, most often — and
     * recolouring the whole face for an identical theme is work for nothing.
     */
    val theme: Flow<WatchThemeState> = flow {
        emitAll(stored())
        emitAll(changes())
    }.distinctUntilChanged()

    /**
     * Exactly one value: the replicated item, or the default.
     *
     * [Tasks.await] blocks, which is what [flowOn] is for, and is what lets a
     * failed read be caught here instead of surfacing on the face. `catch` is
     * the right operator rather than a `try`: it leaves a cancellation of the
     * collecting coroutine alone, so closing the screen still cancels.
     *
     * The timeout matters for the same reason it does on the phone: a blocked
     * `Tasks.await` cannot be interrupted by cancelling the coroutine around it.
     * It is short, because what waits on it is the colour of a watch face — a
     * read that has not answered in two seconds should let the default draw and
     * be corrected by the listener when it does.
     */
    private fun stored(): Flow<WatchThemeState> = flow {
        val items = Tasks.await(
            client.getDataItems(uri, DataClient.FILTER_LITERAL),
            READ_TIMEOUT_SECONDS,
            TimeUnit.SECONDS,
        )
        try {
            emit(items.firstOrNull()?.toThemeState() ?: WatchThemeSync.Unsynced)
        } finally {
            // An item is only valid until the buffer is released, which is why
            // the state is read out above rather than the buffer handed back.
            items.release()
        }
    }.flowOn(Dispatchers.IO).catch { failure ->
        Log.i(TAG, "No stored theme to read", failure)
        emit(WatchThemeSync.Unsynced)
    }

    private fun changes(): Flow<WatchThemeState> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { events ->
            for (event in events) {
                if (event.dataItem.uri.path != WatchThemeSync.PATH) continue
                when (event.type) {
                    DataEvent.TYPE_CHANGED -> trySend(event.dataItem.toThemeState())
                    // The phone cleared it, so the watch goes back to knowing
                    // nothing rather than holding the last theme it was told.
                    DataEvent.TYPE_DELETED -> trySend(WatchThemeSync.Unsynced)
                }
            }
        }

        try {
            client.addListener(listener, uri, DataClient.FILTER_LITERAL)
        } catch (failure: Exception) {
            // Nothing to listen with — no Play Services on this watch. The
            // stored value above already stands, so the face is themed; it
            // just will not follow a later change. `close()` is not only
            // tidiness: `callbackFlow` throws if its block returns without the
            // channel being closed, and this path never reaches `awaitClose`.
            Log.i(TAG, "Not listening for theme changes", failure)
            close()
            return@callbackFlow
        }

        awaitClose { runCatching { client.removeListener(listener) } }
    }.buffer(
        // Only the newest theme matters; a backlog of superseded ones does not.
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private fun DataItem.toThemeState(): WatchThemeState {
        val map = DataMapItem.fromDataItem(this).dataMap
        return WatchThemeSync.read(
            choice = map.getString(WatchThemeSync.KEY_CHOICE),
            // Absent is not false: `read` decides what a missing flag means, and
            // `getBoolean` would answer that question with a silent `false`.
            systemDark = if (map.containsKey(WatchThemeSync.KEY_SYSTEM_DARK)) {
                map.getBoolean(WatchThemeSync.KEY_SYSTEM_DARK)
            } else {
                null
            },
        )
    }

    private companion object {
        const val TAG = "WatchThemeSource"

        /** See [stored]. Short on purpose: a face is waiting on it. */
        const val READ_TIMEOUT_SECONDS = 2L
    }
}
