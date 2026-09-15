package com.github.daanbouwman.flightplanner.startup

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.daanbouwman.flightplanner.startup.CheckResult.Status
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The self-check's one-line verdict, composed from the state's counts through
 * the plural resources. The counts are the ViewModel's (see
 * [StartupCheckViewModelTest]); this pins the words those counts become,
 * including that failures outrank notes and that one and many read differently.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StartupHeadlineTest {

    @get:Rule
    val compose = createComposeRule()

    private fun check(status: Status) = CheckResult("check", status, "detail")

    private fun finished(vararg statuses: Status) = StartupUiState(checks = statuses.map(::check), finished = true)

    /** Composes every state's headline once and returns them, each also asserted on screen. */
    private fun headlines(vararg states: StartupUiState): List<String> {
        val texts = MutableList(states.size) { "" }
        compose.setContent {
            Column {
                states.forEachIndexed { i, state ->
                    texts[i] = headline(state)
                    Text(texts[i])
                }
            }
        }
        texts.distinct().forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        return texts
    }

    @Test
    fun `while running it says so, and everything passing is all clear`() {
        headlines(
            StartupUiState(checks = listOf(check(Status.PASS)), finished = false),
            finished(Status.PASS, Status.PASS, Status.PASS, Status.PASS, Status.PASS),
        ) shouldBe listOf("Checking…", "Everything works")
    }

    @Test
    fun `one failure and many failures are pluralised`() {
        headlines(
            finished(Status.FAIL, Status.PASS),
            finished(Status.FAIL, Status.FAIL),
        ) shouldBe listOf("1 check failed", "2 checks failed")
    }

    @Test
    fun `notes are counted when nothing failed, and a failure outranks them`() {
        headlines(
            finished(Status.WARN, Status.PASS),
            finished(Status.WARN, Status.WARN),
            finished(Status.WARN, Status.FAIL),
        ) shouldBe listOf("Working, with 1 note", "Working, with 2 notes", "1 check failed")
    }
}
