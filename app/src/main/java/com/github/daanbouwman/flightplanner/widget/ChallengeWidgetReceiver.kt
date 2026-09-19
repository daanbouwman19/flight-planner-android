package com.github.daanbouwman.flightplanner.widget

import androidx.glance.appwidget.GlanceAppWidget

/** "Today's challenge" — see [DailyWidgetReceiver] for what a broadcast does. */
class ChallengeWidgetReceiver : DailyWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChallengeWidget()
    override val refresh: MidnightRefresh get() = MidnightRefresh.CHALLENGE
}
