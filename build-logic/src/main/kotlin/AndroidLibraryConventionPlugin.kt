import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // AGP 9 has built-in Kotlin support and rejects `org.jetbrains.kotlin.android`.
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            enableKotlin = true
            compileSdk = libs.int("compileSdk")

            defaultConfig {
                minSdk = libs.int("minSdk")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = JVM_VERSION
                targetCompatibility = JVM_VERSION
            }

            testOptions.unitTests {
                isIncludeAndroidResources = true
                isReturnDefaultValues = true
            }
        }

        configureKotlinAndroid()
        configureUnitTestPlatform()
        configureInvariantChecks()
    }
}
