plugins {
    alias(libs.plugins.flightplanner.android.library)
    alias(libs.plugins.flightplanner.android.compose)
}

android {
    namespace = "com.github.daanbouwman.flightplanner.core.designsystem"
}

dependencies {
    // FlightRules is a domain enum and the badge that renders it belongs here, so
    // the design system knows about the model. It must never know about the
    // database or the network.
    api(projects.core.model)

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
    // graphics-shapes supplies RoundedPolygon and Morph, the types `MaterialShapes`
    // is a catalogue of. material3 pulls it in transitively, but it is declared
    // here because this module names those types in its own public surface -- a
    // morph is a design-system concept, so consumers need them on the classpath.
    api(libs.androidx.graphics.shapes)
    api(libs.compose.animation)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotest.assertions.core)
}
