package com.github.daanbouwman.flightplanner.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.github.daanbouwman.flightplanner.ui.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Publishes the widget picker's previews, once per app version.
 *
 * Android 15 lets an app hand the picker a rendered preview instead of a
 * static image, and Glance draws each from its widget's own `providePreview` —
 * the same code as the widget, so it can never drift from it. The system
 * rate-limits the call and a preview only changes when the code does, so the
 * version that last published is remembered and the call is skipped on every
 * ordinary launch.
 *
 * **Both receivers, and the stamp is set only if both succeeded.** One stamp
 * covers the pair because they are published together and version together;
 * the cost of that is that a rate limit on either one re-publishes both next
 * launch, which is a repeated no-op rather than a defect.
 *
 * Composed by `MainActivity` *after* the app's own content and run on the
 * default dispatcher, so it never precedes the first frame; a rate-limited or
 * failed attempt is simply not stamped and tried again next launch. Before the
 * app has ever run, the picker shows the launcher icon — the price of not
 * shipping hand-drawn `previewImage`s that would go stale.
 */
@Composable
fun PublishWidgetPreview() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            runCatchingCancellable {
                val stamp = WidgetEntryPoint.from(context).widgetPreviewStamp()
                val versionCode = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
                if (stamp.publishedVersion() == versionCode) return@withContext
                val manager = GlanceAppWidgetManager(context)
                val published = listOf(
                    manager.setWidgetPreviews(ChallengeWidgetReceiver::class),
                    manager.setWidgetPreviews(AircraftWidgetReceiver::class),
                )
                if (published.all { it == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS }) {
                    stamp.setPublishedVersion(versionCode)
                }
            }
        }
    }
}
