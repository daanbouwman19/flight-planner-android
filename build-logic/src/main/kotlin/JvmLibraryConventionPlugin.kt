import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

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

        // kotlin-test resolves to its JUnit 5 variant automatically once
        // useJUnitPlatform() is set, but the engine itself still has to be on the
        // runtime classpath or the suite silently reports zero tests.
        dependencies.add(
            "testRuntimeOnly",
            dependencies.platform(libs.findLibrary("junit5-bom").get()),
        )
        dependencies.add("testRuntimeOnly", libs.findLibrary("junit5-jupiter-engine").get())
        dependencies.add("testRuntimeOnly", libs.findLibrary("junit5-platform-launcher").get())

        tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
            testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }
}
