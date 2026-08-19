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
 * - **No debug variant.** `:app`'s debug APK is `debuggable`, and a debuggable
 *   process runs largely interpreted. Every number taken from one describes the
 *   debugger, so `connectedDebugAndroidTest` must not exist to be picked by
 *   accident. The build types that remain both come from `release`, by way of
 *   `androidx.baselineprofile` applied in the consuming module: `benchmarkRelease`
 *   is minified release code, debug-signed and profileable — the variant every
 *   number is taken from — and `nonMinifiedRelease` exists only so a generated
 *   profile names real classes instead of `a.b.c`.
 * - **No `androidx.benchmark.suppressErrors`.** The library refuses to run
 *   against a debuggable or unrooted-emulator target, and that refusal is the
 *   check that would have caught an afternoon of comparing a `benchmark` build
 *   against a `debug` one. Suppressing it would restore exactly the failure mode
 *   this module exists to remove.
 *
 * This plugin used to hand-write a `benchmark` build type here and a matching one
 * in `:app`. Both are gone: `androidx.baselineprofile` produces the same thing,
 * and keeping a third build type alongside its two made the test module a cross
 * product — `connectedBenchmarkBenchmarkAndroidTest` and three siblings, two of
 * them measuring a non-minified target.
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
