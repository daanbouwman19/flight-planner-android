package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.LocalFlightRulesColors
import com.github.daanbouwman.flightplanner.model.FlightRules

/**
 * The flight category of a station, as a chip.
 *
 * Colours come from [LocalFlightRulesColors], never from the Material colour
 * scheme — see `FlightRulesColors` for why that distinction is load-bearing.
 *
 * The category is stated in text as well as in colour, because roughly one man
 * in twelve cannot tell this particular green from this particular red, and
 * "IFR" printed on the chip costs three characters.
 */
@Composable
fun FlightRulesBadge(rules: FlightRules, modifier: Modifier = Modifier) {
    val colors = LocalFlightRulesColors.current[rules]
    Surface(
        modifier = modifier.semantics {
            contentDescription = rules.description.substringBefore('\n')
        },
        shape = MaterialTheme.shapes.small,
        color = colors.container,
        contentColor = colors.onContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(text = rules.code, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@LightDarkPreview
@Composable
private fun FlightRulesBadgePreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlightRules.entries.forEach { FlightRulesBadge(it) }
        }
    }
}
