package com.github.daanbouwman.flightplanner.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The system's handle on the widget; Glance does the rest.
 *
 * Two extra actions are folded into an ordinary update rather than handled
 * here: [ChallengeRefresh.ACTION_NEW_DAY] from the midnight alarm and the
 * system's `TIMEZONE_CHANGED`. Both mean "the date may have changed", and the
 * right response to that is exactly what an `APPWIDGET_UPDATE` does, through
 * Glance's own session plumbing — so the receiver rewrites the intent and
 * lets the superclass take it. Nothing else happens in this process for a
 * broadcast: a receiver that reaches for the index is a receiver that gets
 * killed mid-read.
 */
class ChallengeWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = ChallengeWidget()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ChallengeRefresh.ACTION_NEW_DAY, Intent.ACTION_TIMEZONE_CHANGED -> {
                val ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, ChallengeWidgetReceiver::class.java))
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
        ChallengeRefresh.cancel(context)
    }
}
