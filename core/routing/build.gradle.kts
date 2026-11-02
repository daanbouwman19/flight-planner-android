plugins {
    alias(libs.plugins.flightplanner.jvm.library)
}

dependencies {
    api(projects.core.model)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    // ProjectOutlineRegressionTest pins what MapFrame.projectOutline makes of the
    // outline the app actually ships, not a synthetic one: the hot paths it
    // guards only run hot on a real coastline.
    val outline = rootProject.layout.projectDirectory.file("app/src/main/assets/maps/land.outline").asFile
    inputs.file(outline)
    systemProperty("flightplanner.worldOutline", outline.absolutePath)
}
