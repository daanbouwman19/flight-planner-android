import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // AGP 9 has built-in Kotlin support and rejects `org.jetbrains.kotlin.android`.
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
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

            packaging {
                resources.excludes += setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "/META-INF/LICENSE*",
                    "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                )
            }

            testOptions.unitTests {
                isIncludeAndroidResources = true
                isReturnDefaultValues = true
            }
        }

        configureKotlinAndroid()
        // The library plugin has always armed this; the application plugin did
        // not, which left `:app` silently on JUnit 4 while every other module ran
        // JUnit 5. The visible symptom is that `kotlin.test.Test` does not
        // resolve here and only here, so a test written the way the rest of the
        // repo writes them fails to compile for no apparent reason.
        configureUnitTestPlatform()
        configureInvariantChecks()

        // A screen composed of a `SnackbarHost` and a `FloatingActionButton` as
        // siblings (Logbook is the one screen this app has of that shape — see
        // `LogbookOverlay`'s KDoc) can only be proven to lay its Undo action
        // outside the FAB's bounds by actually composing and measuring it, which
        // needs a real (if simulated) Android environment on the JVM test
        // classpath: Robolectric plus Compose's own test harness. `testOptions.
        // unitTests.isIncludeAndroidResources` above is already on, which is what
        // lets Robolectric resolve this module's real resources rather than
        // Compose's `isReturnDefaultValues` stub. `RobolectricTestRunner` is a
        // plain JUnit 4 `Runner`, so the vintage engine is what lets the JUnit 5
        // platform this module already runs on (`configureUnitTestPlatform`
        // above) discover and execute it.
        dependencies {
            val composeBom = libs.findLibrary("compose-bom").get()
            add("testImplementation", platform(composeBom))
            add("testImplementation", libs.findLibrary("compose-ui-test-junit4").get())
            add("testImplementation", libs.findLibrary("robolectric").get())
            add("testImplementation", libs.findLibrary("junit4").get())
            add("testRuntimeOnly", libs.findLibrary("junit5-vintage-engine").get())
        }
    }
}
