package com.github.daanbouwman.flightplanner.wear.tile

import androidx.compose.ui.graphics.toArgb
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import java.time.ZonedDateTime

/**
 * The four inks the tile is painted in, as ARGB.
 *
 * Taken from the face's own [ColorScheme] rather than authored again, so the
 * tile follows the phone's theme exactly as the face does — Cockpit amber,
 * Chart paper and all. A tile is not Compose and cannot read a `MaterialTheme`;
 * it is a serialised layout the system renders, so the colours cross as numbers.
 */
internal data class TileInks(
    val background: Int,
    val accent: Int,
    val ink: Int,
    val secondaryInk: Int,
) {
    companion object {
        fun from(scheme: ColorScheme) = TileInks(
            background = scheme.background.toArgb(),
            accent = scheme.primary.toArgb(),
            ink = scheme.onBackground.toArgb(),
            secondaryInk = scheme.onSurfaceVariant.toArgb(),
        )
    }
}

/** The words the tile shows, resolved from resources by the service. */
internal data class TileText(
    val label: String,
    val unavailable: String,
    /** Spoken for the whole tile when there is a route; see [challengeTileLayout]. */
    val description: String?,
)

/**
 * The tile: a label, the pair of codes, the figures and the airframe, centred.
 *
 * **Deliberately not the face.** The face is a full-bleed map with the codes at
 * 30 sp across its top; a tile is glanced at in a carousel between other tiles,
 * and what a glance wants is the route in words. The map would also need a
 * bitmap resource regenerated each day — a tile cannot draw a path — for a
 * picture the tap is one step from anyway.
 *
 * Laid out against the middle of the circle, where the chord is the full
 * width, and inset by [SIDE_INSET] of it: the lesson WEAR-PLAN.md's *What the
 * device found* records is that text near the top or bottom of a round screen
 * has far less room than the diameter suggests, and a centred stack is the
 * shape that keeps every line near the middle. The airframe name sits lowest
 * and is the longest line, so it wraps rather than running under the bezel.
 *
 * Sizes are `sp`, so the tile follows the wearer's font scale; the codes and
 * the name may wrap to a second line at large scales rather than clip.
 */
internal fun challengeTileLayout(
    challenge: TileChallenge,
    text: TileText,
    inks: TileInks,
    screenWidthDp: Int,
    clickable: ModifiersBuilders.Clickable,
): LayoutElement {
    val side = dp(screenWidthDp * SIDE_INSET)
    val column = Column.Builder()
        .setWidth(expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .addContent(line(text.label, inks.accent, LABEL_SP, bold = true))
        .addContent(gap(6f))

    when (challenge) {
        is TileChallenge.Ready -> {
            val card = challenge.card
            column
                .addContent(
                    line(
                        "${card.departureIcao} → ${card.destinationIcao}",
                        inks.ink,
                        CODES_SP,
                        bold = true,
                        maxLines = 2,
                    ),
                )
                .addContent(gap(6f))
                .addContent(line("${card.distanceText} · ${card.eteText}", inks.ink, FIGURES_SP))
                .addContent(gap(4f))
                .addContent(line(card.aircraftName, inks.secondaryInk, AIRFRAME_SP, maxLines = 2))
        }

        TileChallenge.Unavailable ->
            column.addContent(line(text.unavailable, inks.secondaryInk, FIGURES_SP, maxLines = 3))
    }

    val modifiers = ModifiersBuilders.Modifiers.Builder()
        .setClickable(clickable)
        .setBackground(
            ModifiersBuilders.Background.Builder()
                .setColor(argb(inks.background))
                .build(),
        )
        .setPadding(
            ModifiersBuilders.Padding.Builder()
                .setStart(side)
                .setEnd(side)
                .build(),
        )
    text.description?.let { spoken ->
        // One description for the whole tile, as the face has one for the
        // whole face: read line by line, "E H A M arrow E G L L" is noise.
        modifiers.setSemantics(
            ModifiersBuilders.Semantics.Builder()
                .setContentDescription(spoken)
                .build(),
        )
    }

    return Box.Builder()
        .setWidth(expand())
        .setHeight(expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setModifiers(modifiers.build())
        .addContent(column.build())
        .build()
}

private fun line(
    value: String,
    color: Int,
    sizeSp: Float,
    bold: Boolean = false,
    maxLines: Int = 1,
): LayoutElement = Text.Builder()
    .setText(value)
    .setMaxLines(maxLines)
    .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
    .setFontStyle(
        FontStyle.Builder()
            .setSize(sp(sizeSp))
            .setColor(argb(color))
            .setWeight(
                if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL,
            )
            .build(),
    )
    .build()

private fun gap(heightDp: Float): LayoutElement = Spacer.Builder().setHeight(dp(heightDp)).build()

/**
 * How long until the tile's answer goes stale: the next local midnight.
 *
 * The challenge is a property of the date, so the tile is fresh until the date
 * changes and never before. The system treats freshness as a hint — it may ask
 * later, and asks anyway whenever the tile is scrolled to — which is why
 * [WatchChallengeSource] also keys its memo on the date rather than trusting
 * this to be the only refresh. Floored at a minute so a request landing a
 * moment before midnight does not ask to be refreshed in a busy loop.
 */
internal fun millisUntilNextDay(now: ZonedDateTime): Long {
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    return (midnight.toInstant().toEpochMilli() - now.toInstant().toEpochMilli())
        .coerceAtLeast(MIN_FRESHNESS_MILLIS)
}

private const val MIN_FRESHNESS_MILLIS = 60_000L

/**
 * The horizontal inset, as a fraction of the screen width, each side.
 *
 * 0.14 leaves 72 % of a 226 dp face — 163 dp — for the codes. `EHAM → EGLL` at
 * [CODES_SP] needs about 140 dp, so a letter-heavy pair still fits on one line
 * at font scale 1.0; above that it wraps at the arrow, which is where a pair of
 * codes may break.
 */
private const val SIDE_INSET = 0.14f

private const val LABEL_SP = 12f
private const val CODES_SP = 22f
private const val FIGURES_SP = 14f
private const val AIRFRAME_SP = 13f
