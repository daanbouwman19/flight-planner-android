package com.github.daanbouwman.flightplanner.widget

import androidx.glance.appwidget.GlanceAppWidget

/** "Aircraft of the day" — see [DailyWidgetReceiver] for what a broadcast does. */
class AircraftWidgetReceiver : DailyWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AircraftWidget()
    override val refresh: MidnightRefresh get() = MidnightRefresh.AIRCRAFT
}
