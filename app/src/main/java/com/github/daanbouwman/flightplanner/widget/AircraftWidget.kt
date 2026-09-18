package com.github.daanbouwman.flightplanner.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.github.daanbouwman.flightplanner.MainActivity
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.launch.LaunchIntents.putLaunchRequest
import com.github.daanbouwman.flightplanner.launch.LaunchRequest
import com.github.daanbouwman.flightplanner.settings.AppSettings
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * "Aircraft of the day": one airframe from your fleet each day.
 *
 * The second widget into the same slot as [ChallengeWidget], and deliberately
 * the same object: the `surfaceContainer` card at a 24 dp corner, the same
 * figure chips, the same big code on the base line, the same midnight alarm —
 * all of it from [WidgetCard]. What changes is what the card is *about*. A
 * challenge is a route, so it has geography and the card draws it; an airframe
 * is an envelope, so the band the challenge fills with a map this one leaves
 * empty and the figures carry the content: range, and the runway it needs.
 * **No map**, because a single aircraft has no geography to draw — a decorative
 * one would be the card claiming to say something it does not.
 *
 * Three bands, as on the challenge card: the airframe and the date along the
 * top, the type code and its flown status on the base line, the figures closing
 * it. Two layouts, chosen by width — the compact one keeps the range alone and
 * states the flown status as a dot rather than a word.
 *
 * The pick is [com.github.daanbouwman.flightplanner.routing.dailyAircraft], and
 * like the challenge's it depends on the date and the fleet and nothing else, so
 * everyone with the same fleet sees the same airframe. Marking it flown repaints
 * the badge at the next render; it never changes *which* airframe today's is.
 *
 * It runs outside any Activity, so the graph is reached through
 * [WidgetEntryPoint], and the whole update is computed *before* `provideContent`
 * — that call does not return until the session ends, so anything after it (the
 * midnight alarm) would never run. A tap opens the airframe in Fleet through the
 * same `LaunchIntents` contract the shortcuts and the challenge widget use.
 */
class AircraftWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, WIDE))

    /** The wide layout, for the same reason [ChallengeWidget.previewSizeMode] names one: it is the widget being sold. */
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(setOf(WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        MidnightRefresh.AIRCRAFT.scheduleNext(context)

        val graph = WidgetEntryPoint.from(context)
        val settings = graph.settingsRepository().settings.filterNotNull().first()
        val state = graph.aircraftSource().load(date = LocalDate.now(), unit = settings.unitSystem)
        val palette = widgetPalette(settings, context)

        provideContent { WidgetTheme(palette) { AircraftSurface(state, palette) } }
    }

    /** The picker's preview: a plausible day, drawn by the same code, in the default settings' colours. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val palette = widgetPalette(AppSettings(), context)
        provideContent { WidgetTheme(palette) { AircraftSurface(PREVIEW_STATE, palette) } }
    }

    companion object {
        /**
         * The narrow bucket: the range alone, and the flown status as a dot.
         *
         * 180 dp rather than the challenge's 140 dp because this card's anchor
         * line carries a *type* code beside a status mark rather than two
         * airport codes at the corners, and 140 dp leaves the airframe name
         * with nothing to say. The provider's `minResizeWidth` matches.
         */
        val COMPACT: DpSize = DpSize(180.dp, 100.dp)

        /** Four cells wide: both figures, and the status in words. */
        val WIDE: DpSize = DpSize(250.dp, 100.dp)

        internal val PREVIEW_STATE = AircraftState.Ready(
            date = LocalDate.of(2026, 9, 16),
            name = "Boeing 787-9",
            typeCode = "B789",
            flown = true,
            rangeText = "7,355 NM",
            runwayText = "9,800 ft",
            airframeId = 0,
        )
    }
}

/** The tappable card. Separate from [AircraftContent] so a test can compose the content alone. */
@Composable
private fun AircraftSurface(state: AircraftState, palette: WidgetPalette) {
    val context = LocalContext.current
    val request = when (state) {
        is AircraftState.Ready -> LaunchRequest.OpenAircraft(state.airframeId)
        // The fleet list rather than a detail, there being no airframe to name:
        // `OpenAircraft(null)` lands on Fleet, which is where both an empty
        // fleet and an unreadable one get fixed.
        AircraftState.FleetEmpty, AircraftState.Unavailable -> LaunchRequest.OpenAircraft(null)
    }
    val open = actionStartActivity(Intent(context, MainActivity::class.java).putLaunchRequest(request))
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            // `surfaceContainer`, as the challenge card is, and for the reason
            // set out there: the two widgets sit on one home screen and the app's
            // cards are neutral surfaces, not Glance's accent `widgetBackground`.
            .surface(palette.card)
            .cornerRadius(WidgetCorner)
            .clickable(open),
    ) {
        AircraftContent(state, palette)
    }
}

/**
 * The card's face: the airframe and the date along the top, the type code and
 * its status on the base line, the figures closing it.
 *
 * The airframe's full [AircraftState.Ready.name] on both layouts, ellipsised
 * when it does not fit. **A divergence from the mock**, which shortens
 * "Cessna 172 Skyhawk" to "Cessna 172" on the compact card: the fleet stores a
 * manufacturer and a variant and no short form, and a rule for inventing one
 * ("drop the last word") is wrong for "Boeing 787-9" the first time it is
 * applied. An honest truncation beats a wrong name.
 */
@Composable
fun AircraftContent(
    state: AircraftState,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    val wide = LocalSize.current.width >= WIDE_THRESHOLD

    Column(modifier = modifier.fillMaxSize().padding(WidgetPadding)) {
        when (state) {
            is AircraftState.Ready -> {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.name,
                        style = TextStyle(
                            color = colors.onSurface,
                            fontSize = if (wide) 13.sp else 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Spacer(GlanceModifier.width(WidgetFigureGap))
                    // The locale from the widget's own configuration, as the
                    // challenge card reads it, and the weekday dropped: this
                    // card's top line has an airframe name to fit beside it.
                    val locale = context.resources.configuration.locales[0]
                    Text(
                        text = state.date.format(DateTimeFormatter.ofPattern("d MMM", locale)),
                        style = TextStyle(color = colors.onSurfaceVariant, fontSize = if (wide) 11.sp else 10.sp),
                        maxLines = 1,
                    )
                }
                // The empty band the challenge card fills with its map. It takes
                // whatever height the card has, so both cards' base lines meet.
                Spacer(GlanceModifier.defaultWeight())
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.typeCode, style = codeStyle(colors.onSurface))
                    Spacer(GlanceModifier.defaultWeight())
                    if (wide) FlownBadge(state.flown, palette) else FlownDot(state.flown)
                }
                Spacer(GlanceModifier.height(8.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Figure(context.getString(R.string.widget_aircraft_chip_range), state.rangeText, palette.chip)
                    // The runway is the figure that goes when there is no room
                    // for it, and the one that is absent when the airframe names
                    // no takeoff distance.
                    if (wide && state.runwayText != null) {
                        Spacer(GlanceModifier.width(WidgetFigureGap))
                        Figure(context.getString(R.string.widget_aircraft_chip_runway), state.runwayText, palette.chip)
                    }
                }
            }

            AircraftState.FleetEmpty -> Message(context.getString(R.string.widget_aircraft_fleet_empty))
            AircraftState.Unavailable -> Message(context.getString(R.string.widget_aircraft_unavailable))
        }
    }
}

/**
 * The flown status in words, on the wide card.
 *
 * Flown is the accented state and not-flown the quiet one, as the mock draws
 * them. The mock's quiet state is an *outlined* pill, which Glance cannot draw
 * — it has no border modifier, and a stroke drawable's colour is fixed at
 * inflate time, so it could not follow Cockpit or Chart — so the quiet state is
 * the figure chips' own surface instead. Same two-step recession, in the
 * vocabulary the launcher has.
 */
@Composable
private fun FlownBadge(flown: Boolean, palette: WidgetPalette) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    val fill = if (flown) GlanceModifier.background(colors.secondaryContainer) else GlanceModifier.surface(palette.chip)
    Text(
        text = context.getString(if (flown) R.string.widget_aircraft_flown else R.string.widget_aircraft_not_flown),
        style = TextStyle(
            color = if (flown) colors.onSecondaryContainer else colors.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        ),
        maxLines = 1,
        modifier = fill.cornerRadius(WidgetChipCorner).padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

/**
 * The flown status as a mark, on the compact card, where the words do not fit.
 *
 * An [Image] of a white oval tinted at display time rather than a coloured box,
 * for two reasons: the tint follows the launcher's day and night on its own, as
 * the challenge card's map layers do, and `Image` is the one Glance element that
 * takes a `contentDescription` — so the compact card still states the status to
 * a screen reader instead of leaving it to colour alone.
 */
@Composable
private fun FlownDot(flown: Boolean) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    Image(
        provider = ImageProvider(R.drawable.widget_status_dot),
        contentDescription = context.getString(
            if (flown) R.string.fleet_detail_flown else R.string.fleet_detail_not_flown,
        ),
        modifier = GlanceModifier.size(DOT),
        colorFilter = ColorFilter.tint(if (flown) colors.secondary else colors.onSurfaceVariant),
    )
}

private val DOT = 10.dp

/** Below this width the second figure and the status words go. */
private val WIDE_THRESHOLD = 220.dp
