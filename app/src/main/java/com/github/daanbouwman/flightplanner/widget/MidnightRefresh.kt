package com.github.daanbouwman.flightplanner.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.ZoneId

/**
 * Brings a widget to the new day.
 *
 * Both home-screen widgets take the local date as an input — "Today's
 * challenge" draws the day's route, "Aircraft of the day" the day's airframe —
 * so what they need is a wall-clock event at local midnight, and that is what
 * [AlarmManager] with `RTC` is. The alternatives were each the wrong clock:
 * `updatePeriodMillis` cannot name a time and wakes the device for nothing; a
 * WorkManager `initialDelay` counts elapsed time, so it ignores a clock or zone
 * change and Doze may hold it well past midnight. This alarm is inexact and
 * non-waking on purpose — nobody looks at a widget on a dark screen, and it
 * fires on the next wake — and it needs no permission. `set` with the same
 * `PendingIntent` replaces the previous alarm, so re-arming on every render is
 * idempotent.
 *
 * A time-zone change is a manifest broadcast each receiver also listens for; a
 * *date* change is not deliverable to a manifest receiver, hence the alarm.
 * Reboot needs nothing: the system re-sends an update to every provider with
 * instances when the user unlocks, which renders and therefore re-arms.
 *
 * One instance per receiver, rather than one alarm that fans out, so that each
 * widget owns its own schedule: `onDisabled` cancels the alarm of the widget
 * that just left the home screen and leaves the other one running. The two
 * `PendingIntent`s already differ by target component — `filterEquals` compares
 * it — and the distinct [requestCode]s make that independence explicit rather
 * than incidental.
 */
class MidnightRefresh private constructor(
    private val receiver: Class<out BroadcastReceiver>,
    private val requestCode: Int,
) {

    fun scheduleNext(context: Context) {
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
        requestCode,
        Intent(context, receiver).setAction(ACTION_NEW_DAY),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {

        /**
         * Shared by both widgets: the receivers are distinct components, and
         * each only ever sees its own explicit Intent, so the action says what
         * happened rather than to whom.
         */
        const val ACTION_NEW_DAY: String = "com.github.daanbouwman.flightplanner.widget.NEW_DAY"

        /** The instant local midnight next occurs after [now] in [zone], in epoch millis. */
        fun nextLocalMidnightMillis(now: Instant, zone: ZoneId): Long =
            now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        /** Request code 0, as before this class was parameterised, so an alarm armed by an older build is replaced rather than orphaned. */
        val CHALLENGE: MidnightRefresh = MidnightRefresh(ChallengeWidgetReceiver::class.java, requestCode = 0)

        val AIRCRAFT: MidnightRefresh = MidnightRefresh(AircraftWidgetReceiver::class.java, requestCode = 1)
    }
}
