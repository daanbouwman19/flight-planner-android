package com.github.daanbouwman.flightplanner.world

import android.content.Context
import android.util.Log
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.routing.WorldOutlineCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the coastline the route cards draw on.
 *
 * An interface over [AssetWorldOutlineLoader], for the same reason the
 * repositories in `:core:database` are interfaces over their `Default…`
 * implementations: a consumer needs "give me the outline", and a ViewModel test
 * should not have to stand up an Android `Context` and an asset manager to ask
 * for one.
 */
fun interface WorldOutlineLoader {

    /** The outline, decoded once per process. Empty when it could not be read. */
    suspend fun load(): WorldOutline
}

/**
 * Reads the prebuilt world outline asset.
 *
 * **Nothing here runs before the first frame.** No `Application.onCreate`, no
 * eager `@Singleton` work: the constructor takes a `Context` and does nothing
 * with it until a screen asks. The airport index is the cautionary tale —
 * rebuilding it on the startup path measured 646 ms against a 500 ms budget —
 * and this is a nineteen-kilobyte read that would still be nineteen kilobytes of
 * somebody's cold start.
 *
 * ### A failure costs the coastline, not the screen
 *
 * A missing or corrupt asset yields [WorldOutline.Empty] rather than throwing.
 * The card's content is the route, and it draws perfectly well over an empty
 * ocean; taking the Plan screen down because a background failed to load would be
 * a far worse trade than losing the background. It is logged, because "the cards
 * have no land on them" is otherwise undiagnosable from a bug report.
 */
@Singleton
class AssetWorldOutlineLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WorldOutlineLoader {

    private val mutex = Mutex()

    @Volatile
    private var cached: WorldOutline? = null

    /**
     * Every visible card draws from the same instance — it is 122 rings of
     * primitive arrays and none of them is ever mutated — so the read and the
     * decode happen once and concurrent callers wait on the same lock rather than
     * each producing a copy.
     */
    override suspend fun load(): WorldOutline {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: read()?.also { cached = it } ?: WorldOutline.Empty
        }
    }

    /** @return the decoded outline, or null when it could not be read. */
    private suspend fun read(): WorldOutline? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.assets.open(OUTLINE_ASSET_PATH).use { it.readBytes() }
            WorldOutlineCodec.decode(bytes)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            // Not cached, so the next screen to ask tries again. Caching the
            // failure would turn one bad read into a process that never draws
            // land again, and the retry costs a file open.
            Log.e(TAG, "World outline failed to load; cards will draw without land", failure)
            null
        }
    }

    private companion object {
        const val OUTLINE_ASSET_PATH = "maps/land.outline"
        const val TAG = "WorldOutlineLoader"
    }
}
