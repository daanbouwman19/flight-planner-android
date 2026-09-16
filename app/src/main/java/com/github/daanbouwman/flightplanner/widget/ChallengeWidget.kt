package com.github.daanbouwman.flightplanner.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Density
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.github.daanbouwman.flightplanner.MainActivity
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.renderRouteMapLayers
import com.github.daanbouwman.flightplanner.launch.LaunchIntents.putLaunchRequest
import com.github.daanbouwman.flightplanner.launch.LaunchRequest
import com.github.daanbouwman.flightplanner.navigation.Destination
import com.github.daanbouwman.flightplanner.routing.RouteArc
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.settings.AppSettings
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * "Today's challenge": one route a day, the same for everyone with your fleet.
 *
 * The route is [com.github.daanbouwman.flightplanner.routing.dailyChallenge];
 * this is the glass around it, and the glass is the route card taken out onto
 * the home screen. The map is the card's background, full bleed and faint —
 * land at 8 %, coast at 16 %, the cased route the one saturated mark — and
 * everything the card says is printed over it: the airframe and the date
 * along the top, the two codes at the bottom corners, the figures closing it.
 * The date is the one line the in-app card does not carry, and it is the
 * reason this route is *today's*.
 *
 * ### The map is three tinted masks, not a picture
 *
 * Glance cannot draw. It can show a bitmap, and it can tint one with a colour
 * *provider* that resolves at display time. So the map is rendered off-screen
 * as alpha masks by the design system's own `renderRouteMapLayers` — the same
 * geometry and widths the Plan screen's cards draw with — and each layer is
 * tinted here: land with `onSurface`, the casing with the card's own
 * `surfaceContainer` tone (see [WidgetPalette]), the route with `primary`. A bitmap coloured up front would keep yesterday's
 * palette across a night-mode switch until the alarm re-rendered it; a tinted
 * mask follows the launcher's day and night, and the wallpaper, on its own.
 *
 * It runs in the app's process but outside any Activity, so the graph is
 * reached through [WidgetEntryPoint], and the whole update is computed *before*
 * `provideContent` — that call does not return until the session ends, so
 * anything after it (the midnight alarm) would never run. Two layouts, chosen
 * by width: the compact one drops the airframe line. Both are one tap to the
 * route's detail through the same [LaunchIntents] contract the shortcuts use.
 */
class ChallengeWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, WIDE))

    /**
     * The picker draws the preview at the widget's default 4×2 cells, but
     * composes it at whatever size the mode names — with no size named it
     * came out below the wide threshold and lost the airframe line. Naming
     * the wide layout is what makes the preview look like the widget it sells.
     */
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(setOf(WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        ChallengeRefresh.scheduleNextMidnight(context)

        val graph = WidgetEntryPoint.from(context)
        // `settings` is a StateFlow started eagerly in the application scope
        // and null only until the first read of a small file has landed.
        val settings = graph.settingsRepository().settings.filterNotNull().first()
        val state = graph.challengeSource().load(
            date = LocalDate.now(),
            unit = settings.unitSystem,
            icaoOnly = settings.icaoOnly,
        )
        val outline = graph.worldOutlineLoader().load()
        val palette = widgetPalette(settings, context)

        provideContent { Themed(palette) { ChallengeSurface(state, outline, palette) } }
    }

    /**
     * The picker's preview: a plausible day, drawn by the same code over the
     * real coast, in the default settings' colours — dynamic, following the system.
     */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val outline = WidgetEntryPoint.from(context).worldOutlineLoader().load()
        val palette = widgetPalette(AppSettings(), context)
        provideContent { Themed(palette) { ChallengeSurface(PREVIEW_STATE, outline, palette) } }
    }

    companion object {
        /** Two cells wide: codes and figures, no airframe line. */
        val COMPACT: DpSize = DpSize(140.dp, 100.dp)

        /** Four cells wide: the full card. */
        val WIDE: DpSize = DpSize(250.dp, 100.dp)

        internal val PREVIEW_STATE = ChallengeState.Ready(
            date = LocalDate.of(2026, 9, 16),
            departureIcao = "EHAM",
            destinationIcao = "KJFK",
            aircraftName = "Boeing 787-9",
            distanceText = "3,162 NM",
            eteText = "6:33",
            arc = RouteArc.sampleGeographic(52.3086, 4.7639, 40.6398, -73.7789, samples = RouteArc.CARD_SAMPLES),
            route = Destination.RouteDetail("EHAM", "KJFK", aircraftId = 0, distanceNm = 3_162),
        )
    }
}

/** The map, as Android bitmaps Glance can show. One per [com.github.daanbouwman.flightplanner.core.designsystem.components.RouteMapLayer]. */
class ChallengeMap(val land: Bitmap, val casing: Bitmap, val route: Bitmap)

/**
 * Renders the map for a card of [size] at [density], or null when the state
 * has no route. Pixels, not dp: the bitmap is shown `FillBounds` in a box of
 * exactly this size, so rendering at the box's own pixel size is what keeps a
 * 1 dp coast one device pixel wide.
 */
fun renderChallengeMap(state: ChallengeState, outline: WorldOutline, size: DpSize, density: Density): ChallengeMap? {
    val ready = state as? ChallengeState.Ready ?: return null
    val layers = with(density) {
        renderRouteMapLayers(
            arc = ready.arc,
            outline = outline,
            widthPx = size.width.roundToPx(),
            heightPx = size.height.roundToPx(),
            density = density,
            // The band the airframe line is printed across; the route is framed
            // below it, exactly as on the route card.
            topInsetPx = (PADDING + TITLE_LINE).toPx(),
        )
    } ?: return null
    return ChallengeMap(
        land = layers.land.asAndroidBitmap(),
        casing = layers.casing.asAndroidBitmap(),
        route = layers.route.asAndroidBitmap(),
    )
}

/** The tappable card. Separate from [ChallengeContent] so a test can compose the content alone. */
@Composable
private fun ChallengeSurface(state: ChallengeState, outline: WorldOutline, palette: WidgetPalette) {
    val context = LocalContext.current
    val size = LocalSize.current
    val request = when (state) {
        is ChallengeState.Ready -> LaunchRequest.OpenRoute(state.route)
        ChallengeState.FleetEmpty, ChallengeState.Unavailable -> LaunchRequest.GenerateRoutes
    }
    val open = actionStartActivity(Intent(context, MainActivity::class.java).putLaunchRequest(request))
    // Once per size, not per recomposition: Glance composes each responsive
    // size separately and this is three bitmap fills.
    val map = remember(state, outline, size) {
        renderChallengeMap(state, outline, size, Density(context.resources.displayMetrics.density))
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            // The route card's own `surfaceContainer`, not Glance's `widgetBackground`:
            // the latter is the wallpaper's *secondary accent* tone
            // (`system_accent2_800` at night), and the app's cards are neutral
            // surfaces. A card tinted where every card in the app is not would be
            // the one thing on the home screen that did not look like the app.
            // See `WidgetPalette` for where the tone comes from.
            .surface(palette.card)
            .cornerRadius(CORNER)
            .clickable(open),
    ) {
        ChallengeContent(state, map, palette)
    }
}

/** `GlanceTheme` with the palette's colours, or Glance's own dynamic ones when it has none. */
@Composable
private fun Themed(palette: WidgetPalette, content: @Composable () -> Unit) {
    val colors = palette.colors
    if (colors == null) GlanceTheme(content = content) else GlanceTheme(colors = colors, content = content)
}

/**
 * The card's face: the map behind, the airframe and date along the top, the
 * codes at the bottom corners, the figures closing it — the route card's own
 * grammar, in Glance's vocabulary.
 *
 * @param map the rendered masks, or null to draw the card with no map behind
 *   it — a test, or a state with no route.
 */
@Composable
fun ChallengeContent(
    state: ChallengeState,
    map: ChallengeMap?,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    val wide = LocalSize.current.width >= WIDE_THRESHOLD

    Box(modifier = modifier.fillMaxSize()) {
        if (map != null) {
            // Land, casing, route: the order `RouteMap` paints in.
            MapLayer(map.land, colors.onSurface)
            MapLayer(map.casing, palette.casing)
            MapLayer(map.route, colors.primary)
        }
        Column(modifier = GlanceModifier.fillMaxSize().padding(PADDING)) {
            when (state) {
                is ChallengeState.Ready -> {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (wide) {
                            Text(
                                text = state.aircraftName,
                                style = TextStyle(color = colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight(),
                            )
                        } else {
                            Spacer(GlanceModifier.defaultWeight())
                        }
                        // The locale from the widget's own configuration, which
                        // is what a re-render after a language change sees;
                        // `Locale.getDefault()` in a composable is neither
                        // observable nor guaranteed to be the launcher's.
                        val locale = context.resources.configuration.locales[0]
                        Text(
                            text = state.date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)),
                            style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
                            maxLines = 1,
                        )
                    }
                    // The gap is the map's, and it takes whatever height the card has.
                    Spacer(GlanceModifier.defaultWeight())
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text(state.departureIcao, style = codeStyle(colors.onSurface))
                        Spacer(GlanceModifier.defaultWeight())
                        Text(state.destinationIcao, style = codeStyle(colors.onSurface))
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Figure(context.getString(R.string.plan_chip_distance), state.distanceText, palette.chip)
                        Spacer(GlanceModifier.width(8.dp))
                        Figure(context.getString(R.string.plan_chip_time), state.eteText, palette.chip)
                    }
                }

                ChallengeState.FleetEmpty -> Message(context.getString(R.string.widget_challenge_fleet_empty))
                ChallengeState.Unavailable -> Message(context.getString(R.string.widget_challenge_unavailable))
            }
        }
    }
}

/** One mask of the map, filling the card and coloured by [tint] at display time. */
@Composable
private fun MapLayer(mask: Bitmap, tint: ColorProvider) {
    Image(
        provider = ImageProvider(mask),
        // The map carries nothing the card does not state in text.
        contentDescription = null,
        modifier = GlanceModifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(tint),
    )
}

/** A figure with its label, on a quiet pill so the coast can pass behind it. */
@Composable
private fun Figure(label: String, value: String, chip: WidgetSurface) {
    val colors = GlanceTheme.colors
    Row(
        modifier = GlanceModifier
            .surface(chip)
            .cornerRadius(CHIP_CORNER)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = value,
            style = TextStyle(color = colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun Message(text: String) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(
            text = text,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            maxLines = 3,
        )
    }
}

private fun codeStyle(color: ColorProvider) = TextStyle(color = color, fontSize = CODE_SIZE, fontWeight = FontWeight.Bold)

private val CODE_SIZE = 26.sp
private val PADDING: Dp = 16.dp

/** The height of the airframe line the map keeps its route clear of. */
private val TITLE_LINE: Dp = 20.dp
private val CORNER = 24.dp
private val CHIP_CORNER = 12.dp

/** Below this width the airframe line goes. */
private val WIDE_THRESHOLD = 220.dp
