import com.android.build.api.dsl.TestExtension
import com.android.build.api.variant.TestAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Configures a `com.android.test` module that measures `:app` on a real device.
 *
 * Kept as a convention plugin for the same reason as the others — compileSdk,
 * minSdk and the JVM target come from one place — but it also carries the two
 * settings that decide whether the measurement means anything:
 *
 * - **The `benchmark` build type only.** `:app`'s debug APK is `debuggable`, and
 *   a debuggable process runs largely interpreted. Every number taken from one
 *   describes the debugger. The `benchmark` variant is release code that can be
 *   installed without release keys, so it is the only variant this module builds;
 *   there is deliberately no debug counterpart to pick by accident.
 * - **No `androidx.benchmark.suppressErrors`.** The library refuses to run
 *   against a debuggable or unrooted-emulator target, and that refusal is the
 *   check that would have caught an afternoon of comparing a `benchmark` build
 *   against a `debug` one. Suppressing it would restore exactly the failure mode
 *   this module exists to remove.
 */
class AndroidBenchmarkConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // AGP 9 has built-in Kotlin support and rejects `org.jetbrains.kotlin.android`.
        pluginManager.apply("com.android.test")

        extensions.configure<TestExtension> {
            enableKotlin = true
            compileSdk = libs.int("compileSdk")

            defaultConfig {
                minSdk = libs.int("minSdk")
                targetSdk = libs.int("targetSdk")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = JVM_VERSION
                targetCompatibility = JVM_VERSION
            }

            // A `create`d build type inherits no signing config, and an unsigned
            // test APK fails to install with INSTALL_PARSE_FAILED_NO_CERTIFICATES —
            // which reads like a broken APK rather than a missing four lines.
            val debugSigning = signingConfigs.getByName("debug")

            buildTypes {
                // The test APK itself must be debuggable — the benchmark library
                // needs to read its own results back out — while the *target* it
                // measures is release code. Those are different APKs, so there is
                // no contradiction here.
                create("benchmark") {
                    signingConfig = debugSigning
                    isDebuggable = true
                    matchingFallbacks += listOf("release")
                }
            }
        }

        // Removes the variant that would measure `:app`'s debug APK. Leaving it
        // there means `connectedDebugAndroidTest` exists, tab-completes, and
        // reports plausible-looking numbers for a debuggable target — which is
        // precisely the mistake that once produced two contradictory sessions and
        // an afternoon of explaining the wrong thing.
        extensions.configure<TestAndroidComponentsExtension> {
            beforeVariants(selector().withBuildType("debug")) { variant -> variant.enable = false }
        }

        configureKotlinAndroid()
    }
}
