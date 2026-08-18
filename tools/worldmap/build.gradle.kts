plugins {
    alias(libs.plugins.flightplanner.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    application
}

// This module builds the shipped world outline asset. It is never on the app
// classpath: the device reads the binary blob it produces and never sees GeoJSON.
application {
    mainClass.set("com.github.daanbouwman.flightplanner.worldmap.MainKt")
}

// All default paths in the tool are repo-relative, so run from the repo root
// rather than the module directory.
val repoRoot = rootProject.layout.projectDirectory.asFile

tasks.named<JavaExec>("run") {
    workingDir = repoRoot
    description = "Rebuilds app/src/main/assets/maps/land.outline from the Natural Earth snapshot."
    group = "build"
}

/** Convenience alias matching the name used in the README. */
tasks.register<JavaExec>("buildWorldOutline") {
    description = "Alias for :tools:worldmap:run."
    group = "build"
    workingDir = repoRoot
    mainClass.set("com.github.daanbouwman.flightplanner.worldmap.MainKt")
    classpath = sourceSets.getByName("main").runtimeClasspath
}

/**
 * Fails the build if the checked-in outline is missing, malformed, or no longer
 * agrees with the source snapshot it claims to come from.
 */
val verifyWorldOutline = tasks.register<JavaExec>("verifyWorldOutline") {
    description = "Verifies the shipped world outline asset."
    group = "verification"
    workingDir = repoRoot
    mainClass.set("com.github.daanbouwman.flightplanner.worldmap.VerifyKt")
    classpath = sourceSets.getByName("main").runtimeClasspath

    inputs.file(File(repoRoot, "app/src/main/assets/maps/land.outline"))
    inputs.dir(File(repoRoot, "tools/worldmap/data"))
}

tasks.named("check") { dependsOn(verifyWorldOutline) }

dependencies {
    // WorldOutline and its codec are shared with the app: the builder must write
    // exactly what the device reads, so there is one definition of the format.
    implementation(projects.core.routing)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.assertions.core)
}
