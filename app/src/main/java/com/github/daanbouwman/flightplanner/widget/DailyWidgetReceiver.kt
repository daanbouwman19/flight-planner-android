package com.github.daanbouwman.flightplanner.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The system's handle on a widget whose content is a function of the local
 * date; Glance does the rest.
 *
 * Two extra actions are folded into an ordinary update rather than handled
 * here: [MidnightRefresh.ACTION_NEW_DAY] from the widget's own midnight alarm
 * and the system's `TIMEZONE_CHANGED`. Both mean "the date may have changed",
 * and the right response to that is exactly what an `APPWIDGET_UPDATE` does,
 * through Glance's own session plumbing — so the receiver rewrites the intent
 * and lets the superclass take it. Nothing else happens in this process for a
 * broadcast: a receiver that reaches for the index is a receiver that gets
 * killed mid-read.
 *
 * Each subclass owns one [refresh], and the two do not share an alarm: the one
 * cancelled in [onDisabled] is the alarm of the widget whose last instance just
 * left the home screen, and the other widget's midnight is untouched.
 */
abstract class DailyWidgetReceiver : GlanceAppWidgetReceiver() {

    /** This widget's midnight alarm, armed by its `provideGlance`. */
    protected abstract val refresh: MidnightRefresh

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MidnightRefresh.ACTION_NEW_DAY, Intent.ACTION_TIMEZONE_CHANGED -> {
                // `javaClass` is the concrete receiver the manifest names, so
                // each widget rewrites the broadcast for its own instances only.
                val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, javaClass))
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
        refresh.cancel(context)
    }
}
