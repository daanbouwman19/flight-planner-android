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
    //
    // graphics-shapes is `api` because the morphing shapes built from it appear in
    // this module's own public surface, so consumers need the types on their
    // classpath. It is the library `MaterialShapes` is a catalogue over; that
    // catalogue is absent from material3 1.4.0 (docs/API-GROUND-TRUTH.md).
    api(libs.androidx.graphics.shapes)
    api(libs.compose.animation)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.assertions.core)
}
