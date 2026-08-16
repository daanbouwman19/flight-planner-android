plugins {
    alias(libs.plugins.flightplanner.android.library)
    alias(libs.plugins.flightplanner.android.compose)
}

android {
    namespace = "com.github.daanbouwman.flightplanner.core.designsystem"
}

dependencies {
    api(libs.compose.material3.adaptive)
    api(libs.compose.material3.adaptive.layout)
    api(libs.compose.material3.adaptive.navigation)
    api(libs.compose.material3.adaptive.navigation.suite)
    // material-icons-extended is deliberately absent. Nothing uses it yet, and it
    // carries ~10,000 vector icons, so pulling it in "just in case" is a habit
    // worth not starting. (Removing it did not measurably shrink the debug APK
    // here -- the bulk is Compose, Filament and Hilt -- but an unused dependency
    // of that size should not sit on the classpath.)
    implementation(libs.androidx.core.ktx)
}
