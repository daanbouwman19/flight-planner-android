import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
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
