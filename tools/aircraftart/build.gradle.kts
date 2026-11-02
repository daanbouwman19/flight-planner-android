plugins {
    alias(libs.plugins.flightplanner.jvm.library)
    application
}

// This module builds the shipped aircraft silhouettes. It is never on the app
// classpath: it reads the flight simulator's own 3D models, which only exist on
// the machine the sim is installed on, and writes the generated table that
// `:core:aircraftart` compiles. CI cannot run it; what CI can check — every seed
// row has an outline, every path fills its declared box — is `:core:aircraftart`'s
// unit tests against the generated table.
application {
    mainClass.set("com.github.daanbouwman.flightplanner.aircraftart.MainKt")
}

// All default paths in the tool are repo-relative, so run from the repo root
// rather than the module directory.
val repoRoot = rootProject.layout.projectDirectory.asFile

tasks.named<JavaExec>("run") {
    workingDir = repoRoot
    description = "Rebuilds the aircraft silhouettes from the flight simulator's models. Pass --args=\"build --sim-root H:/flightsim\"."
    group = "build"
    // A model is rasterised at a few thousand pixels a side, and the distance
    // transform behind the morphological opening holds a float per pixel.
    maxHeapSize = "3g"
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.assertions.core)
}
