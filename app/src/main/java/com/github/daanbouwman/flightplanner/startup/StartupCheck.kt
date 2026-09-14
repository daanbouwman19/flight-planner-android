package com.github.daanbouwman.flightplanner.startup

/** One line of the on-device self-check. */
data class CheckResult(
    val name: String,
    val status: Status,
    val detail: String,
) {
    enum class Status { PASS, WARN, FAIL, RUNNING }
}

/**
 * The self-check's progress. The headline that summarises it is the screen's
 * to compose — it is a translated, plural-aware string resource, and a state
 * class has no resources.
 */
data class StartupUiState(
    val checks: List<CheckResult> = emptyList(),
    val finished: Boolean = false,
) {
    val failures: Int get() = checks.count { it.status == CheckResult.Status.FAIL }
    val warnings: Int get() = checks.count { it.status == CheckResult.Status.WARN }
}
