package com.github.daanbouwman.flightplanner.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.ZoneId

/**
 * Brings the widget to the new day.
 *
 * The widget's only input is the local date, so what it needs is a wall-clock
 * event at local midnight, and that is what [AlarmManager] with `RTC` is. The
 * alternatives were each the wrong clock: `updatePeriodMillis` cannot name a
 * time and wakes the device for nothing; a WorkManager `initialDelay` counts
 * elapsed time, so it ignores a clock or zone change and Doze may hold it well
 * past midnight. This alarm is inexact and non-waking on purpose — nobody
 * looks at a widget on a dark screen, and it fires on the next wake — and it
 * needs no permission. `set` with the same `PendingIntent` replaces the
 * previous alarm, so re-arming on every render is idempotent.
 *
 * A time-zone change is a manifest broadcast the receiver also listens for; a
 * *date* change is not deliverable to a manifest receiver, hence the alarm.
 * Reboot needs nothing: the system re-sends an update to every provider with
 * instances when the user unlocks, which renders and therefore re-arms.
 */
object ChallengeRefresh {

    const val ACTION_NEW_DAY: String = "com.github.daanbouwman.flightplanner.widget.NEW_DAY"

    /** The instant local midnight next occurs after [now] in [zone], in epoch millis. */
    fun nextLocalMidnightMillis(now: Instant, zone: ZoneId): Long =
        now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun scheduleNextMidnight(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.set(
            AlarmManager.RTC,
            nextLocalMidnightMillis(Instant.now(), ZoneId.systemDefault()),
            newDayIntent(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(newDayIntent(context))
    }

    private fun newDayIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ChallengeWidgetReceiver::class.java).setAction(ACTION_NEW_DAY),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
