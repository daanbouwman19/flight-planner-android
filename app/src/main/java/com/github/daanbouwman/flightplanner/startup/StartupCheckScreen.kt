package com.github.daanbouwman.flightplanner.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * A temporary screen that reports whether the stack works on this device.
 *
 * It exists so the first install proves something rather than showing a title.
 * The Plan screen replaces it in M3.
 */
@Composable
fun StartupCheckScreen(viewModel: StartupCheckViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text("Flight Planner", style = MaterialTheme.typography.headlineMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!state.finished) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            state.headline,
                            style = MaterialTheme.typography.titleMedium,
                            color = when {
                                !state.finished -> MaterialTheme.colorScheme.onSurfaceVariant
                                state.failures > 0 -> MaterialTheme.colorScheme.error
                                else -> STATUS_PASS
                            },
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "On-device self-check. The real interface arrives in the next milestone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.checks) { check -> CheckCard(check) }
        }
    }
}

@Composable
private fun CheckCard(check: CheckResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = check.status.glyph(),
                    color = check.status.color(),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = check.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(
                text = check.detail,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val STATUS_PASS = Color(0xFF2E7D32)
private val STATUS_WARN = Color(0xFFE08600)

private fun CheckResult.Status.glyph(): String = when (this) {
    CheckResult.Status.PASS -> "✓"
    CheckResult.Status.WARN -> "!"
    CheckResult.Status.FAIL -> "✗"
    CheckResult.Status.RUNNING -> "…"
}

@Composable
private fun CheckResult.Status.color(): Color = when (this) {
    CheckResult.Status.PASS -> STATUS_PASS
    CheckResult.Status.WARN -> STATUS_WARN
    CheckResult.Status.FAIL -> MaterialTheme.colorScheme.error
    CheckResult.Status.RUNNING -> MaterialTheme.colorScheme.onSurfaceVariant
}
