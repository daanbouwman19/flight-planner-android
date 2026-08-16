import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure

/**
 * Pure-JVM modules (`:core:model`, `:core:routing`, `:tools:airportdb`).
 *
 * These deliberately have no Android dependency so their tests run in a plain
 * JVM in milliseconds, and so no `Context` can leak into the domain layer.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        configureKotlinJvm()
        configureUnitTestPlatform()
    }
}
