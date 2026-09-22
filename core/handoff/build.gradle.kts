plugins {
    alias(libs.plugins.flightplanner.jvm.library)
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    // `checkAll` is a suspend function, so the property tests run inside `runTest`.
    testImplementation(libs.kotlinx.coroutines.test)
}
