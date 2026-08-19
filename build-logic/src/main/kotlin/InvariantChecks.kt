import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * The one module allowed to touch the APIs the restricted rules forbid.
 *
 * Both of those rules exist to keep a blast radius small rather than to ban an
 * API outright, so exactly one module has to be able to use them — otherwise the
 * facade in front of them could not be written.
 */
private const val DESIGN_SYSTEM_PATH = ":core:designsystem"

/**
 * One source-level invariant from `CLAUDE.md`, as something the build can fail on.
 *
 * A rule is a line regex plus the sentence a developer needs to read when it
 * fires. It matches per line, after comment lines are dropped — see
 * [CheckInvariantsTask.check] for why that matters.
 */
private class Invariant(
    /** Short name, printed in the failure so the rule can be found in this file. */
    val id: String,
    val pattern: Regex,
    /** True for rules `:core:designsystem` is exempt from. */
    val designSystemExempt: Boolean,
    val explanation: String,
)

/**
 * Material 3 Expressive symbols, as substrings of an imported name.
 *
 * Matched as a substring rather than as a whole word so the variants come along
 * with the base names: `HorizontalFloatingToolbar` and `FloatingToolbarDefaults`
 * are exactly as much of a leak as `FloatingToolbar`, and listing every one of
 * them here would be a list that goes stale on the next alpha.
 */
private val EXPRESSIVE_SYMBOLS = listOf(
    "MaterialShapes",
    "MotionScheme",
    "LoadingIndicator",
    "ButtonGroup",
    "FloatingToolbar",
    "SplitButton",
    "FloatingActionButtonMenu",
    "WavyProgressIndicator",
)

private val INVARIANTS = listOf(
    Invariant(
        id = "expressive-outside-designsystem",
        pattern = Regex(
            "^\\s*import\\s+androidx\\.compose\\.material3\\.(?:[A-Za-z0-9_]+\\.)*" +
                "[A-Za-z0-9_]*(" + EXPRESSIVE_SYMBOLS.joinToString("|") + ")[A-Za-z0-9_]*",
        ),
        designSystemExempt = true,
        explanation = "Material 3 Expressive is reached only through " + DESIGN_SYSTEM_PATH +
            ". material3 is pinned to an alpha above the Compose BOM; surfacing what you " +
            "need from there first is what keeps a breaking bump a one-module change.",
    ),
    Invariant(
        id = "raw-animation-spec",
        pattern = Regex("^\\s*import\\s+androidx\\.compose\\.animation\\.core\\.(spring|tween)\\b"),
        designSystemExempt = true,
        explanation = "Name a motion token — FlightMotion.spatial(), .effects() — rather than " +
            "spring() or tween(). A screen knows it is moving a card; it does not know it " +
            "wants dampingRatio = 0.8f.",
    ),
    Invariant(
        id = "sdk-int-guard",
        pattern = Regex("\\bBuild\\.VERSION\\.SDK_INT\\b"),
        designSystemExempt = false,
        explanation = "minSdk is the floor, so a guard beneath it is a branch no supported " +
            "device can take: it cannot be tested and it rots silently. If an API genuinely " +
            "needs a newer platform, raise minSdk rather than branch below it.",
    ),
)

/**
 * Fails the build on the source-level invariants in `CLAUDE.md`.
 *
 * These are the three the document says are on the author rather than on tooling:
 * Expressive imports escaping `:core:designsystem`, raw `spring()` and `tween()`,
 * and `SDK_INT` guards. All three compile perfectly, so the compiler will never
 * see them, and Android Lint has already been observed to miss one — `ObsoleteSdkInt`
 * did not flag an `SDK_INT` branch below `minSdk`. A regex over the source is a
 * blunt instrument, but the alternative in practice is a reviewer remembering,
 * and that is exactly what let the one through.
 *
 * Deliberately not Detekt. Two of the three are import bans that `ForbiddenImport`
 * would express well, but the third is not an import and would need a custom rule
 * — which means a published rule JAR and a plugin to load it, to enforce three
 * lines. This has no dependency and states what it checks in the file that checks it.
 */
@CacheableTask
abstract class CheckInvariantsTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /**
     * This module's Gradle path, for the failure message.
     *
     * Carried as an input rather than read from `project` in the task action:
     * touching the `Project` at execution time is what breaks the configuration
     * cache, and this task is otherwise a pure function of its source files.
     */
    @get:Input
    abstract val modulePath: Property<String>

    /** Ids of the rules this module is exempt from. Part of the cache key. */
    @get:Input
    abstract val exemptions: SetProperty<String>

    /**
     * Written on success only, so Gradle has an output to hang up-to-date checks
     * on. Its contents are a receipt, not an artefact anything reads.
     */
    @get:OutputFile
    abstract val receipt: RegularFileProperty

    @TaskAction
    fun check() {
        val exempt = exemptions.get()
        val rules = INVARIANTS.filterNot { it.id in exempt }
        val files = sources.files.filter { it.isFile }.sortedBy { it.path }
        val violations = mutableListOf<String>()

        files.forEach { file ->
            file.readLines().forEachIndexed { zeroBased, line ->
                // A rule firing inside a comment is nearly always the comment that
                // explains why the rule exists — CLAUDE.md's own prose names
                // `Build.VERSION.SDK_INT`, and a KDoc quoting it must not fail the
                // build. Commented-out code is caught the moment it is uncommented,
                // which is the moment it starts to matter.
                val trimmed = line.trimStart()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                    return@forEachIndexed
                }
                rules.forEach { rule ->
                    if (rule.pattern.containsMatchIn(line)) {
                        violations += file.path + ":" + (zeroBased + 1) + ": [" + rule.id + "]\n" +
                            "    " + line.trim() + "\n" +
                            "    " + rule.explanation
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            val plural = if (violations.size == 1) "" else "s"
            throw GradleException(
                violations.size.toString() + " invariant violation" + plural + " in " +
                    modulePath.get() + ". These compile; they are still defects.\n\n" +
                    violations.joinToString("\n\n") +
                    "\n\nSee the Invariants section of CLAUDE.md.",
            )
        }

        val receiptFile = receipt.get().asFile
        receiptFile.parentFile.mkdirs()
        receiptFile.writeText(
            "checked " + files.size + " file(s) against " + rules.size + " invariant(s)\n",
        )
    }
}

/**
 * Registers `checkInvariants` for this module and hangs it off `check`.
 *
 * On `check` rather than on `compileKotlin`: a violation is a review finding, not
 * a broken tree, and hooking the compile task would make it impossible to get a
 * running APK onto a device to look at the thing you were in the middle of. Since
 * `./gradlew build` runs `check`, it is still enforced everywhere it matters.
 */
internal fun Project.configureInvariantChecks() {
    // Read outside the configuration block on purpose. Inside it the receiver is
    // the task, where `path` is `:core:designsystem:checkInvariants` rather than
    // `:core:designsystem` — which silently un-exempts the one module that has to
    // be exempt, and does it in the direction that fails a clean tree.
    val modulePath = path
    val ruleExemptions = if (modulePath == DESIGN_SYSTEM_PATH) {
        INVARIANTS.filter { it.designSystemExempt }.map { it.id }
    } else {
        emptyList()
    }
    val sourceTree = fileTree(projectDir) {
        include("src/**/*.kt")
        // Generated and committed. Hand-editing it is its own rule.
        exclude("src/main/generated/**")
    }

    val checkTask = tasks.register("checkInvariants", CheckInvariantsTask::class.java) {
        group = "verification"
        description = "Fails on the source-level invariants in CLAUDE.md."
        sources.from(sourceTree)
        this.modulePath.set(modulePath)
        exemptions.set(ruleExemptions)
        receipt.set(layout.buildDirectory.file("reports/invariants/receipt.txt"))
    }

    tasks.named("check") { dependsOn(checkTask) }
}
