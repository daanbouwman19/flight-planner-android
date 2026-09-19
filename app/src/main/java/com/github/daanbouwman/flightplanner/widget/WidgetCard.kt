package com.github.daanbouwman.flightplanner.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/*
 * The grammar both home-screen cards are drawn in.
 *
 * The app ships two widgets into the same slot — "Today's challenge" and
 * "Aircraft of the day" — and the design asks them to be the same object twice:
 * one 24 dp `surfaceContainer` card, the same figure chips, the same 16 dp
 * gutter, the same big code on the base line. Two copies of those numbers
 * would agree today and drift on the first retune, so they live
 * here and each widget contributes only what it actually says.
 *
 * Everything here is Glance, not Compose: it is composed inside a widget session
 * and cannot use `:core:designsystem`, which is why the card's tones come from
 * `WidgetPalette` instead. See `docs/DESIGN-SYSTEM.md` on the surfaces that have
 * no composition of ours to inherit from.
 */

/** `GlanceTheme` with the palette's colours, or Glance's own dynamic ones when it has none. */
@Composable
fun WidgetTheme(palette: WidgetPalette, content: @Composable () -> Unit) {
    val colors = palette.colors
    if (colors == null) GlanceTheme(content = content) else GlanceTheme(colors = colors, content = content)
}

/**
 * A figure with its label, on a quiet pill.
 *
 * The pill is what lets the challenge card's coastline pass behind a figure
 * without taking the figure with it; the aircraft card has no map and keeps the
 * pill anyway, because the two cards sit on the same home screen and a figure
 * that changed shape between them would read as a different kind of fact.
 *
 * @param label the chart abbreviation, or null for a bare figure where its
 *   unit already says which one it is and a label would not fit.
 */
@Composable
fun Figure(label: String?, value: String, chip: WidgetSurface) {
    val colors = GlanceTheme.colors
    Row(
        modifier = GlanceModifier
            .surface(chip)
            .cornerRadius(WidgetChipCorner)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label != null) {
            Text(
                text = label,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.width(6.dp))
        }
        Text(
            text = value,
            style = TextStyle(color = colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
}

/** What a card says instead of its content when it has none — an empty fleet, an unreadable index. */
@Composable
fun Message(text: String) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(
            text = text,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            maxLines = 3,
        )
    }
}

/** The card's anchor line: an ICAO airport code, or an aircraft type code. */
fun codeStyle(color: ColorProvider): TextStyle =
    TextStyle(color = color, fontSize = WidgetCodeSize, fontWeight = FontWeight.Bold)

val WidgetCodeSize = 26.sp

/** The gutter. Also the band the challenge card's map keeps its route clear of. */
val WidgetPadding: Dp = 16.dp

/** The card's own corner, matching the route card the widgets are a copy of. */
val WidgetCorner: Dp = 24.dp

val WidgetChipCorner: Dp = 12.dp

/** Between two figure chips on one line. */
val WidgetFigureGap: Dp = 8.dp

/**
 * The width from which a card is its wide layout — the challenge card gains
 * its airframe line, the aircraft card its second figure and the status in
 * words. One number for both, between the shared compact bucket (140 dp) and
 * the shared wide one (250 dp), so a launcher grant is never wide for one card
 * and compact for the other.
 */
val WidgetWideThreshold: Dp = 220.dp
