package com.github.daanbouwman.flightplanner.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState

/**
 * How a fleet change reaches a widget session that is already open.
 *
 * Both widgets compute their state in `provideGlance`, before `provideContent`,
 * and Glance runs `provideGlance` once per *session* — a session it keeps open
 * for some seconds after a render, and an `update` that arrives while it is
 * open only recomposes what is there. So "mark it flown, look at the home
 * screen" would recompose the same state and show the old badge, as it did on
 * the phone. What an open session does re-read on `update` is the widget's own
 * Glance state, and [currentState] readers recompose when it changes — so the
 * fleet change is written into that state as a counter, and
 * [reloadOnFleetChange] reloads when the counter it was composed with moves.
 *
 * The counter is per widget instance, as Glance state is, and it means nothing
 * beyond "not the value you last saw".
 */
object FleetRevision {

    private val KEY = intPreferencesKey("fleet_revision")

    /** Bumps every instance of [widget] and asks it to update — the two halves of one change. */
    suspend fun bump(context: Context, widget: GlanceAppWidget) {
        for (id in GlanceAppWidgetManager(context).getGlanceIds(widget.javaClass)) {
            updateAppWidgetState(context, id) { prefs -> prefs[KEY] = (prefs[KEY] ?: 0) + 1 }
            widget.update(context, id)
        }
    }

    /**
     * [initial], until the revision this composition started under changes;
     * then whatever [load] returns. The first composition never loads: the
     * caller computed [initial] a moment ago for this same session.
     */
    @Composable
    fun <T> reloadOnFleetChange(initial: T, load: suspend () -> T): T {
        val revision = currentState<Preferences>()[KEY]
        val composedUnder = remember { revision }
        val state by produceState(initial, revision) {
            if (revision != composedUnder) value = load()
        }
        return state
    }
}
