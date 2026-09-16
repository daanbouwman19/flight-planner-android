package com.github.daanbouwman.flightplanner.ui.goldens

import androidx.compose.runtime.Composable
import com.github.daanbouwman.flightplanner.ui.logbook.LogbookPreviewContent
import com.github.daanbouwman.flightplanner.ui.logbook.LogbookStatus
import com.github.daanbouwman.flightplanner.ui.logbook.LogbookUiState
import com.github.daanbouwman.flightplanner.ui.logbook.groupByMonth
import com.github.daanbouwman.flightplanner.ui.logbook.previewRows
import com.github.daanbouwman.flightplanner.ui.logbook.summarizeYear
import org.junit.Test
import org.robolectric.annotation.Config

/** The Logbook: the year summary strip and rows grouped by month, then empty and loading. */
class LogbookGoldens : GoldenSuite(subject = "logbook", primaryState = "populated") {

    @Composable
    override fun Primary() = LogbookPreviewContent(
        LogbookUiState(
            groups = previewRows.groupByMonth(),
            summary = previewRows.summarizeYear(2026),
            status = LogbookStatus.Ready,
        ),
    )

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun empty() = captureState("empty") {
        LogbookPreviewContent(LogbookUiState(status = LogbookStatus.Ready))
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun loading() = captureState("loading") {
        LogbookPreviewContent(LogbookUiState(status = LogbookStatus.Loading))
    }
}
