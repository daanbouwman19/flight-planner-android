package com.github.daanbouwman.flightplanner.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The system's handle on [AircraftWidget]; Glance does the rest.
 *
 * The same shape as [ChallengeWidgetReceiver] and for the same reasons:
 * [MidnightRefresh.ACTION_NEW_DAY] from its own midnight alarm and the system's
 * `TIMEZONE_CHANGED` both mean "the date may have changed", and the right
 * response to that is exactly what an `APPWIDGET_UPDATE` does — so the receiver
 * rewrites the Intent and lets the superclass take it. Nothing else happens in
 * this process for a broadcast.
 *
 * The two receivers do not share one alarm: each cancels its own in
 * [onDisabled], so removing one widget from the home screen leaves the other
 * one's midnight intact.
 */
class AircraftWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = AircraftWidget()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MidnightRefresh.ACTION_NEW_DAY, Intent.ACTION_TIMEZONE_CHANGED -> {
                val ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, AircraftWidgetReceiver::class.java))
                if (ids.isNotEmpty()) {
                    super.onReceive(
                        context,
                        Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                    )
                }
            }

            else -> super.onReceive(context, intent)
        }
    }

    /** The last instance left the home screen: no more midnights to wait for. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        MidnightRefresh.AIRCRAFT.cancel(context)
    }
}
