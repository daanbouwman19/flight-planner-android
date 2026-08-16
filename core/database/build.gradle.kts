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
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.room.testing)
}
