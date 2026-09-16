package com.github.daanbouwman.flightplanner.ui.goldens

import androidx.compose.runtime.Composable
import com.github.daanbouwman.flightplanner.startup.CheckResult
import com.github.daanbouwman.flightplanner.startup.CheckResult.Status
import com.github.daanbouwman.flightplanner.startup.StartupCheckContent
import com.github.daanbouwman.flightplanner.startup.StartupUiState
import org.junit.Test
import org.robolectric.annotation.Config

/** The self-check with everything passing, mid-run, and with one failure. */
class StartupCheckGoldens : GoldenSuite(subject = "self-check", primaryState = "all-pass") {

    private val passing = listOf(
        CheckResult("Airport database", Status.PASS, "24,321 airports (8,185 with ICAO), 58,221 runway ends in 29 ms"),
        CheckResult("In-memory index", Status.PASS, "24,321 airports in 19 ms\n  read 9 ms, decode 10 ms"),
        CheckResult("Fleet", Status.PASS, "118 aircraft already present"),
        CheckResult("Route generation", Status.PASS, "Top Rudder Aircraft 103 Solo (87 NM)\n  50 routes in 2 ms"),
        CheckResult("Filament (3D globe)", Status.PASS, "Vulkan backend active, feature level FEATURE_LEVEL_3"),
    )

    @Composable
    override fun Primary() = StartupCheckContent(
        state = StartupUiState(checks = passing, finished = true),
        onBack = {},
    )

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun running() = captureState("running") {
        StartupCheckContent(
            state = StartupUiState(
                checks = passing.take(2) + CheckResult("Fleet", Status.RUNNING, ""),
                finished = false,
            ),
            onBack = {},
        )
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun oneFailed() = captureState("one-failed") {
        StartupCheckContent(
            state = StartupUiState(
                checks = passing.dropLast(1) + CheckResult(
                    "Filament (3D globe)",
                    Status.FAIL,
                    "UnsatisfiedLinkError: dlopen failed: library libfilament-jni.so not found",
                ),
                finished = true,
            ),
            onBack = {},
        )
    }
}
