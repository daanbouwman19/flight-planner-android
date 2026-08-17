package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme

/**
 * The label above a group of rows — "This month", "Longest flights", "Weather".
 *
 * Marked as a heading in the semantics tree, which is what lets TalkBack users
 * jump between sections instead of swiping through every row of a long list. It
 * costs one modifier and it is the single cheapest accessibility win available
 * in a list-heavy app.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@LightDarkPreview
@Composable
private fun SectionHeaderPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            SectionHeader(text = "This month")
            SectionHeader(text = "Longest flights")
        }
    }
}
