package com.github.daanbouwman.flightplanner.core.designsystem.export

import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Keeps the React mirror's tokens honest.
 *
 * `design-mirror/` is a React reimplementation of this design system, uploaded to
 * Claude Design so concepts are drawn with the app's real colours, type scale and
 * shapes. A reimplementation is a second source of truth, and the usual fate of a
 * second source of truth is to drift from the first without anyone noticing.
 *
 * So none of its values are written by hand. [DesignTokenExport] reads them off
 * the same objects the app composes with, and this test compares the result with
 * the committed file: change a colour in `ColorSchemes.kt` and this fails until
 * the export is regenerated and committed. The failure is the point — it is the
 * only moment at which the drift is cheap to fix.
 *
 * Regenerate with:
 * ```
 * ./gradlew :core:designsystem:testDebugUnitTest --tests "*DesignTokenExportTest*" -Dtokens.write=true
 * ```
 *
 * The direction of truth is one-way and permanent: Kotlin defines, the mirror
 * follows. A concept drawn in Claude Design comes back as intent — a hierarchy, a
 * colour role, a spacing rhythm — never as pixel values transplanted into Compose.
 */
class DesignTokenExportTest {

    @Test
    fun `committed tokens match the design system`() {
        val expected = DesignTokenExport.buildJson()
        val target = tokensFile()

        if (System.getProperty("tokens.write") == "true") {
            target.parentFile.mkdirs()
            target.writeText(expected)
            return
        }

        if (!target.exists()) {
            target.parentFile.mkdirs()
            target.writeText(expected)
            fail(
                "design-mirror tokens did not exist and have been written to " +
                    "${target.path}. Commit them.",
            )
        }

        val actual = target.readText()
        if (actual != expected) {
            target.writeText(expected)
            fail(
                "design-mirror tokens were stale and have been regenerated at ${target.path}.\n" +
                    "The design system changed; commit the regenerated file so the React mirror " +
                    "renders what the app renders.",
            )
        }
        actual shouldBe expected
    }

    /**
     * `design-mirror/src/tokens/tokens.json`, resolved from the repository root.
     *
     * The working directory of an Android unit test is the module directory, but
     * that is a convention rather than a promise, so the root is found by walking
     * up to the settings file instead of counting `..` segments.
     */
    private fun tokensFile(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        val root = dir ?: error("Could not find the repository root from ${File(".").absolutePath}")
        return File(root, "design-mirror/src/tokens/tokens.json")
    }
}
