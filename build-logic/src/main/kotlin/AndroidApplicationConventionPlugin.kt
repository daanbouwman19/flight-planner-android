import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

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
    }
}
