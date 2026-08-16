plugins {
    alias(libs.plugins.flightplanner.android.library)
    alias(libs.plugins.flightplanner.android.hilt)
    alias(libs.plugins.flightplanner.android.room)
}

android {
    namespace = "com.github.daanbouwman.flightplanner.core.database"
}

dependencies {
    api(projects.core.model)
    // The loader builds the in-memory index directly from a cursor, so it needs
    // the index type; keeping that here avoids materialising entity objects
    // purely to hand them to another layer.
    api(projects.core.routing)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.room.testing)
}
