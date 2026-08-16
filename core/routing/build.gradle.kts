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
