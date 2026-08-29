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
 * **A failing run leaves the tree exactly as it found it.** Regenerating is the
 * explicit `-Dtokens.write=true` below and nothing else. An earlier version wrote
 * the file *and* failed, which meant a second `./gradlew build` passed against
 * what the first run had written — so the guard could be walked past by running it
 * twice, and would then never fire again.
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

        // **The failing run must not repair the tree.** It used to: on a mismatch
        // it wrote the regenerated JSON and then failed, so a second `./gradlew
        // build` passed against the file the first run had just written. A
        // developer who changed a colour, saw the failure, and re-ran would ship
        // the Kotlin change with a stale `tokens.json` and nothing would ever fail
        // again — the exact drift this class exists to make impossible.
        //
        // So it only reports, and regenerating is the explicit `-Dtokens.write=true`
        // above. A build that fails twice for the same reason is the point.
        if (!target.exists()) {
            fail(
                "design-mirror tokens are missing at ${target.path}.\n" +
                    "Generate them with:\n" +
                    "  ./gradlew :core:designsystem:testDebugUnitTest " +
                    "--tests '*DesignTokenExportTest*' -Dtokens.write=true\n" +
                    "then rebuild the mirror and commit both.",
            )
        }

        val actual = target.readText()
        if (actual != expected) {
            fail(
                "design-mirror tokens are stale at ${target.path}.\n" +
                    firstDifference(actual, expected) + "\n" +
                    "The design system changed. Regenerate with:\n" +
                    "  ./gradlew :core:designsystem:testDebugUnitTest " +
                    "--tests '*DesignTokenExportTest*' -Dtokens.write=true\n" +
                    "then `cd design-mirror && npm run build` and commit both, so the " +
                    "React mirror renders what the app renders.",
            )
        }
        actual shouldBe expected
    }

    /**
     * The first line that differs, so the failure names what changed.
     *
     * A 1,400-line JSON diff in a Gradle log is unreadable, and the one thing a
     * reader needs is which token moved.
     */
    private fun firstDifference(actual: String, expected: String): String {
        val a = actual.lines()
        val e = expected.lines()
        for (i in 0 until maxOf(a.size, e.size)) {
            val left = a.getOrNull(i)
            val right = e.getOrNull(i)
            if (left != right) {
                return "First difference at line ${i + 1}:\n" +
                    "  committed: ${left ?: "<end of file>"}\n" +
                    "  current:   ${right ?: "<end of file>"}"
            }
        }
        return "Files differ in trailing whitespace only."
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
