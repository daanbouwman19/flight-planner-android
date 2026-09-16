package com.github.daanbouwman.flightplanner.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.daanbouwman.flightplanner.R

/**
 * The on-device self-check, behind Settings.
 *
 * It was the launch screen before the app had one, and it stays because it is
 * the only thing that proves the prepackaged database, the index, the native
 * libraries and the route generator all work on a real device. What changed is
 * that it is now *somewhere you went*: it has an app bar with a way back, the
 * same shape as the Licences screen beside it in Settings, and its prose says
 * what it is rather than promising an interface that has long since arrived.
 * The check names and details themselves come from the ViewModel and stay as
 * they are — they are diagnostic output, read by whoever files the bug.
 */
@Composable
fun StartupCheckScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StartupCheckViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StartupCheckContent(state = state, onBack = onBack, modifier = modifier)
}

/**
 * The screen minus its ViewModel: the headline and every check card for one
 * [StartupUiState]. [StartupCheckScreen] feeds it the live run; the screenshot
 * goldens feed it finished, running and failed states that a real run only
 * passes through.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StartupCheckContent(
    state: StartupUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.self_check_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        // The bar owns the top inset; the list takes the bottom one itself, as
        // content padding, so the last card scrolls up from under the gesture
        // handle rather than the column stopping short of it.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    ) { insets ->
        val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!state.finished) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = headline(state),
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
                        text = stringResource(R.string.self_check_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.checks) { check -> CheckCard(check) }
        }
    }
}

/**
 * The one-line verdict: still running, how many failed, how many notes, or all
 * clear. `internal` so `StartupHeadlineTest` can compose it on its own and pin
 * the plurals; a state class has no resources, so it cannot live there.
 */
@Composable
internal fun headline(state: StartupUiState): String = when {
    !state.finished -> stringResource(R.string.self_check_headline_checking)
    state.failures > 0 -> pluralStringResource(R.plurals.self_check_headline_failed, state.failures, state.failures)
    state.warnings > 0 -> pluralStringResource(R.plurals.self_check_headline_notes, state.warnings, state.warnings)
    else -> stringResource(R.string.self_check_headline_ok)
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
