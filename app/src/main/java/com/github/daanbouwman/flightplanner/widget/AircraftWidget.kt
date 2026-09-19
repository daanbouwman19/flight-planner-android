package com.github.daanbouwman.flightplanner.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
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
 * is an envelope, so the figures carry the content: range, and the runway it
 * needs. **No map**, because a single aircraft has no geography to draw — a
 * decorative one would be the card claiming to say something it does not.
 *
 * Four layouts, chosen by width and by height independently:
 *
 * - **Short** (one launcher row, the default): two lines. The airframe and the
 *   date along the top, the type code and the range on the base line. This is
 *   the whole fact, and it is why the widget is one row by default — a route
 *   needs a band for its map, an airframe does not.
 * - **Tall** (two rows): the challenge card's three bands — the top line, an
 *   empty band where the challenge draws its map so the two cards' base lines
 *   meet, the code and its status, and the figures closing the card.
 * - **Compact** (two cells) keeps the range alone and states the flown status
 *   as a dot; **wide** (four cells) adds the runway and states the status in
 *   words. On the short card the status moves up to the top line, because the
 *   base line is where the figures are.
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

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, WIDE, COMPACT_TALL, WIDE_TALL))

    /**
     * The compact short layout, for the reason [ChallengeWidget.previewSizeMode]
     * names the wide one: the picker draws the preview at the widget's default
     * cells, and this widget's default is two by one.
     */
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(setOf(COMPACT))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        MidnightRefresh.AIRCRAFT.scheduleNext(context)

        val graph = WidgetEntryPoint.from(context)
        val settings = graph.settingsRepository().settings.filterNotNull().first()
        val palette = widgetPalette(settings, context)
        suspend fun load() = graph.aircraftSource().load(date = LocalDate.now(), unit = settings.unitSystem)
        val initial = load()

        provideContent {
            // Reloaded in place when the fleet changes under an open session —
            // see `FleetRevision`. The date is not re-read: a new day is a new
            // session, by the midnight alarm.
            val state = FleetRevision.reloadOnFleetChange(initial) { load() }
            WidgetTheme(palette) { AircraftSurface(state, palette) }
        }
    }

    /** The picker's preview: a plausible day, drawn by the same code, in the default settings' colours. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val palette = widgetPalette(AppSettings(), context)
        provideContent { WidgetTheme(palette) { AircraftSurface(PREVIEW_STATE, palette) } }
    }

    companion object {
        /** `minHeight` in `widget_aircraft_info.xml`, which explains the number. */
        private val SHORT_HEIGHT: Dp = 40.dp

        /**
         * Two cells by one, the default: the range alone, and the flown status
         * as a dot. The same width bucket as the challenge card's, so a launcher
         * grant is never compact for one widget and wide for the other; the
         * airframe name ellipsises to whatever the top line has room for.
         *
         * **The short buckets are as tall as the provider's height floor, not as
         * the layout.** Glance composes the largest bucket that fits the grant
         * in *both* dimensions and falls back to the smallest when none does —
         * so a short bucket taller than a one-row grant (One UI: 90 dp) would
         * have a four-cell row render the compact layout. Measured, on the
         * phone, with these at 100 dp.
         */
        val COMPACT: DpSize = DpSize(140.dp, SHORT_HEIGHT)

        /** Four cells by one: both figures, and the status in words. */
        val WIDE: DpSize = DpSize(250.dp, SHORT_HEIGHT)

        /**
         * The two-row grants: the challenge card's three bands, from 150 dp —
         * above any one-row grant and below a two-row one (One UI: 204 dp).
         */
        val COMPACT_TALL: DpSize = DpSize(COMPACT.width, 150.dp)
        val WIDE_TALL: DpSize = DpSize(WIDE.width, 150.dp)

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
 * The card's face, in the layout [LocalSize] selects — see [AircraftWidget]
 * for the four.
 *
 * The airframe's full [AircraftState.Ready.name] on every layout, ellipsised
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
    val size = LocalSize.current
    val wide = size.width >= WidgetWideThreshold
    val tall = size.height >= TALL_THRESHOLD

    Column(
        modifier = modifier
            .fillMaxSize()
            // The short card has two lines to fit in a one-row grant (One UI:
            // 90 dp); the gutter gives way vertically, not the lines.
            .padding(horizontal = WidgetPadding, vertical = if (tall) WidgetPadding else SHORT_PADDING),
    ) {
        when (state) {
            is AircraftState.Ready -> {
                // On the tall card the name has the empty band to itself and
                // wraps into it, the date staying on its first line; on the
                // short card it has one line and ellipsises.
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = if (tall) Alignment.Top else Alignment.CenterVertically,
                ) {
                    // On the short compact card the status leads the name: the
                    // base line is taken by the range, and a dot before a name
                    // reads as that name's status.
                    if (!tall && !wide) {
                        FlownDot(state.flown)
                        Spacer(GlanceModifier.width(6.dp))
                    }
                    Text(
                        text = state.name,
                        style = TextStyle(
                            color = colors.onSurface,
                            fontSize = if (tall) 14.sp else if (wide) 13.sp else 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = if (tall) TALL_NAME_LINES else 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Spacer(GlanceModifier.width(WidgetFigureGap))
                    if (!tall && wide) {
                        FlownBadge(state.flown, palette)
                        Spacer(GlanceModifier.width(WidgetFigureGap))
                    }
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
                // The band the challenge card fills with its map, on the tall
                // card; on the short one, what little is left over.
                Spacer(GlanceModifier.defaultWeight())
                if (tall) {
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
                        Figures(state, wide, labelRange = true, palette)
                    }
                } else {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.typeCode, style = codeStyle(colors.onSurface))
                        Spacer(GlanceModifier.defaultWeight())
                        // Beside the type code there is room for the range or
                        // its label, not both; the unit says which figure it is.
                        Figures(state, wide, labelRange = wide, palette)
                    }
                }
            }

            AircraftState.FleetEmpty -> Message(context.getString(R.string.widget_aircraft_fleet_empty))
            AircraftState.Unavailable -> Message(context.getString(R.string.widget_aircraft_unavailable))
        }
    }
}

/**
 * The figure chips: the range, and on the wide card the runway too.
 *
 * The runway is the figure that goes when there is no room for it, and the
 * one that is absent when the airframe names no takeoff distance.
 */
@Composable
private fun Figures(state: AircraftState.Ready, wide: Boolean, labelRange: Boolean, palette: WidgetPalette) {
    val context = LocalContext.current
    Figure(
        label = if (labelRange) context.getString(R.string.widget_aircraft_chip_range) else null,
        value = state.rangeText,
        chip = palette.chip,
    )
    if (wide && state.runwayText != null) {
        Spacer(GlanceModifier.width(WidgetFigureGap))
        Figure(context.getString(R.string.widget_aircraft_chip_runway), state.runwayText, palette.chip)
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

/**
 * The lines a name may take on the tall card: the band above the base line
 * holds three at 14 sp with room over, and no airframe in the seed fleet
 * needs more than two at the compact width.
 */
private const val TALL_NAME_LINES = 3

/** The short card's vertical gutter — see [AircraftContent]. */
private val SHORT_PADDING: Dp = 12.dp

/**
 * From this height the card is the three-band layout. Between the short
 * buckets (40 dp) and the tall ones (150 dp), as [WidgetWideThreshold] sits
 * between the width buckets.
 */
private val TALL_THRESHOLD: Dp = 130.dp
