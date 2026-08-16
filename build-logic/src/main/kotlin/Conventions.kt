import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** The single source of truth for JVM bytecode level across every module. */
internal val JVM_VERSION = JavaVersion.VERSION_17
internal val JVM_TARGET = JvmTarget.JVM_17

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.int(alias: String): Int =
    findVersion(alias).orElseThrow { IllegalStateException("Missing version '$alias' in libs.versions.toml") }
        .requiredVersion
        .toInt()

/**
 * Compiler options shared by every Kotlin module.
 *
 * Note there is deliberately no `-jvm-default` flag: since Kotlin 2.2 the default
 * mode already emits Java default methods for interface members, which is what
 * the Room- and Hilt-generated code needs.
 */
internal fun Project.configureKotlinAndroid() {
    extensions.getByType<KotlinAndroidProjectExtension>().compilerOptions {
        jvmTarget.set(JVM_TARGET)
    }
}

internal fun Project.configureKotlinJvm() {
    extensions.getByType<KotlinJvmProjectExtension>().compilerOptions {
        jvmTarget.set(JVM_TARGET)
    }
}

/**
 * Puts unit tests on JUnit 5 and, crucially, on the runtime classpath.
 *
 * `kotlin-test` resolves to its JUnit 5 variant once `useJUnitPlatform()` is
 * set, but the engine itself is not pulled in transitively. Without it a module
 * does not fail — it reports zero tests and passes, which is the worst possible
 * outcome for a test suite. Gradle's `failOnNoDiscoveredTests` turns that
 * silence into an error, and this makes sure it never has to.
 */
internal fun Project.configureUnitTestPlatform() {
    dependencies.add("testRuntimeOnly", dependencies.platform(libs.findLibrary("junit5-bom").get()))
    dependencies.add("testRuntimeOnly", libs.findLibrary("junit5-jupiter-engine").get())
    dependencies.add("testRuntimeOnly", libs.findLibrary("junit5-platform-launcher").get())

    // Gradle treats "sources exist but nothing ran" as a misconfiguration, which
    // is exactly the failure the engine dependency above prevents — so keep it
    // armed. AGP generates test sources even for a module that has none of its
    // own, though, so ask the source tree directly rather than trusting that.
    val hasTestSources = file("src/test").walkTopDown()
        .any { it.isFile && (it.extension == "kt" || it.extension == "java") }

    tasks.withType(Test::class.java).configureEach {
        useJUnitPlatform()
        failOnNoDiscoveredTests.set(hasTestSources)
        testLogging {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}
