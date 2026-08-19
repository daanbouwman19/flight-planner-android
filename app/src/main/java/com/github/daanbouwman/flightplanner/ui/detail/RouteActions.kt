package com.github.daanbouwman.flightplanner.ui.detail

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Hands a string to another application, and reports the one way that fails.
 *
 * Every external action on the detail screen is the same two steps — build a
 * string, give it away — so they share one object rather than each screen
 * element carrying its own `try`/`catch` around a `startActivity`.
 *
 * **The failure is real and is not hypothetical.** A phone with no browser, or a
 * work profile that blocks one, throws [ActivityNotFoundException] from
 * `startActivity`, and an uncaught one takes the process down from a tap on a
 * link. Every launch here reports it instead.
 *
 * No `<queries>` entry accompanies this. Package visibility governs *asking*
 * which application handles an intent, and nothing here asks: the intent is
 * launched and the failure is handled.
 */
class RouteActionLauncher(
    private val context: Context,
    private val clipboard: Clipboard,
    private val scope: CoroutineScope,
    private val onNoHandler: () -> Unit,
) {

    /** Opens [url] in whatever handles it. */
    fun open(url: String) {
        launch(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    /** Offers [text] to the system share sheet. */
    fun share(text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        launch(Intent.createChooser(send, title))
    }

    /**
     * Copies [text] to the clipboard.
     *
     * No confirmation is shown. Android draws its own the moment anything is
     * copied, and a snackbar underneath it would be the same news twice.
     */
    fun copy(label: String, text: String) {
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
        }
    }

    private fun launch(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (missing: ActivityNotFoundException) {
            Log.w(TAG, "Nothing handles $intent", missing)
            onNoHandler()
        }
    }

    private companion object {
        const val TAG = "RouteActions"
    }
}

/**
 * The launcher, bound to this composition's context, clipboard and scope.
 *
 * [onNoHandler] is deliberately **not** a key. Callers pass a lambda that closes
 * over a `CoroutineScope`, which the Compose compiler treats as unstable, so a
 * fresh instance arrives on every recomposition — keying on it meant the
 * `remember` never hit and a new launcher was allocated on every pass, which is
 * the opposite of what the call looked like it was doing. Read through a
 * [rememberUpdatedState] instead, so the launcher is built once and still calls
 * the newest lambda.
 */
@Composable
fun rememberRouteActionLauncher(
    scope: CoroutineScope,
    onNoHandler: () -> Unit,
): RouteActionLauncher {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val currentOnNoHandler by rememberUpdatedState(onNoHandler)
    return remember(context, clipboard, scope) {
        RouteActionLauncher(context, clipboard, scope) { currentOnNoHandler() }
    }
}
