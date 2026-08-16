plugins {
    alias(libs.plugins.flightplanner.android.library)
    alias(libs.plugins.flightplanner.android.compose)
}

android {
    namespace = "com.github.daanbouwman.flightplanner.feature.globe"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.designsystem)

    // Filament: Kotlin API over a Vulkan backend. Prebuilt native libraries ship
    // in the AAR, so no C/C++ is written or compiled here.
    implementation(libs.filament.android)
    implementation(libs.filament.utils.android)

    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
}
