plugins {
    alias(libs.plugins.flightplanner.jvm.library)
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
}
