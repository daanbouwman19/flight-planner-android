package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.withTabularFigures
import java.util.Locale

/**
 * One figure in a [StatSummaryStrip] — a caption and a value that counts up.
 *
 * [value] is the raw integer the count-up animates; [format] turns the figure
 * the animation is currently on into the string the tile shows. It is a lambda
 * rather than a fixed formatter so the same animated integer can be presented
 * as a plain count, a grouped distance, or minutes reinterpreted as `H:MM` —
 * whichever the tile is for — all through
 * [com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion.rememberCountUp]
 * rather than three separate animations.
 */
@Immutable
data class StatTile(
    val label: String,
    val value: Int,
    val format: (Int) -> String = { it.toString() },
    /** Spoken instead of the label and value when the two together would be ambiguous out of context. */
    val contentDescription: String? = null,
)

/**
 * A row of hero figures, each counting up once when it appears or changes.
 *
 * Deliberately not [ValueChip]: that component is a small label-left/value-right
 * chip sized to sit over a route card's map. A summary strip wants the opposite
 * grammar — a handful of large, centred, captioned figures side by side, the
 * way a statistics screen states a headline number — so it is its own
 * component rather than a chip stretched into a shape it was not built for.
 */
@Composable
fun StatSummaryStrip(tiles: List<StatTile>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tiles.forEach { tile ->
            val animated = FlightMotion.rememberCountUp(tile.value)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .semantics(mergeDescendants = true) {
                        tile.contentDescription?.let { contentDescription = it }
                    },
            ) {
                Text(
                    text = tile.format(animated),
                    style = MaterialTheme.typography.titleLarge.withTabularFigures(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = tile.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@LightDarkPreview
@CompactWidthPreview
@Composable
private fun StatSummaryStripPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        StatSummaryStrip(
            tiles = listOf(
                StatTile(label = "FLIGHTS", value = 42),
                StatTile(label = "NM", value = 48_213, format = { String.format(Locale.ROOT, "%,d", it) }),
                StatTile(
                    label = "HOURS",
                    value = 3_805,
                    format = { String.format(Locale.ROOT, "%d:%02d", it / 60, it % 60) },
                ),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
