plugins {
    alias(libs.plugins.flightplanner.android.library)
}

android {
    namespace = "com.github.daanbouwman.flightplanner.mapscene"
}

// The route map as geometry and draw calls: the scene builder, its draw order,
// the ink every map draws with, and the parsed aircraft outlines. Both apps draw
// through it — the phone via `:core:designsystem`'s `RouteMap`, `NetworkMap` and
// widget masks, the watch face directly — so a stroke width, an arrowhead or the
// rule for when the aircraft fits is one decision rather than two copies of it.
//
// It is Compose *graphics* and nothing else, and it must stay that way. The watch
// cannot depend on `:core:designsystem`, because that module is built on the
// phone's `androidx.compose.material3`, pinned to an alpha for the Expressive
// surface, and a watch APK must never carry it (see CLAUDE.md, "The watch never
// imports the phone's Material"). So this module takes no Material of any kind,
// no `flightplanner.android.compose` (which adds exactly that library), and no
// Compose compiler: nothing here is `@Composable`. Like the design system, it
// never knows the database or the network.

dependencies {
    // MapFrame, WorldOutline and GeoArc appear in the scene builder's signature,
    // and AircraftShape in its aircraft parameter: a caller cannot build a scene
    // without naming them, so both are api.
    api(projects.core.routing)
    api(projects.core.aircraftart)

    // Path, DrawScope and Color are the scene's own surface, so the graphics
    // layer is api too. It brings ui-geometry and ui-unit (Offset, Density) with
    // it. Versions come from the BOM, as everywhere else.
    implementation(platform(libs.compose.bom))
    api(libs.compose.ui.graphics)

    // The JUnit 5 variant by name; see `:core:network`'s build file.
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotest.assertions.core)
}
