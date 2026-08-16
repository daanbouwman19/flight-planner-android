plugins {
    alias(libs.plugins.flightplanner.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    application
}

// This module builds the shipped airport database asset. It is never on the
// app classpath.
application {
    mainClass.set("com.github.daanbouwman.flightplanner.airportdb.MainKt")
}

// All default paths in the tool are repo-relative, so run from the repo root
// rather than the module directory.
val repoRoot = rootProject.layout.projectDirectory.asFile

tasks.named<JavaExec>("run") {
    workingDir = repoRoot
    description = "Rebuilds app/src/main/assets/databases/airports.db from the OurAirports snapshots."
    group = "build"
}

/** Convenience alias matching the name used in the README. */
tasks.register<JavaExec>("buildAirportDb") {
    description = "Alias for :tools:airportdb:run."
    group = "build"
    workingDir = repoRoot
    mainClass.set("com.github.daanbouwman.flightplanner.airportdb.MainKt")
    classpath = sourceSets.getByName("main").runtimeClasspath
}

/**
 * Fails the build if the checked-in asset does not match the current Room schema.
 *
 * Without this, editing a database entity and forgetting to regenerate the asset
 * produces a build that looks fine and an app that cannot open its database on
 * any device.
 */
val verifyAirportAsset = tasks.register<JavaExec>("verifyAirportAsset") {
    description = "Verifies the shipped airport database against the current Room schema."
    group = "verification"
    workingDir = repoRoot
    mainClass.set("com.github.daanbouwman.flightplanner.airportdb.VerifyKt")
    classpath = sourceSets.getByName("main").runtimeClasspath

    inputs.file(File(repoRoot, "app/src/main/assets/databases/airports.db"))
    inputs.dir(File(repoRoot, "core/database/schemas"))
}

tasks.named("check") { dependsOn(verifyAirportAsset) }

dependencies {
    implementation(projects.core.model)
    implementation(libs.sqlite.jdbc)
    implementation(libs.kotlin.csv.jvm)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.assertions.core)
}
