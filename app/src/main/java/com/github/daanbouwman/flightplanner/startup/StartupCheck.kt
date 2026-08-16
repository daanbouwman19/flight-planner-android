package com.github.daanbouwman.flightplanner.startup

/** One line of the on-device self-check. */
data class CheckResult(
    val name: String,
    val status: Status,
    val detail: String,
) {
    enum class Status { PASS, WARN, FAIL, RUNNING }
}

data class StartupUiState(
    val checks: List<CheckResult> = emptyList(),
    val finished: Boolean = false,
) {
    val failures: Int get() = checks.count { it.status == CheckResult.Status.FAIL }
    val warnings: Int get() = checks.count { it.status == CheckResult.Status.WARN }

    val headline: String
        get() = when {
            !finished -> "Checking…"
            failures > 0 -> "$failures check${if (failures == 1) "" else "s"} failed"
            warnings > 0 -> "Working, with $warnings note${if (warnings == 1) "" else "s"}"
            else -> "Everything works"
        }
}
