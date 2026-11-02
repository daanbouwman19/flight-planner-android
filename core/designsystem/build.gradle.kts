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

    // RouteMap names GeoArc, WorldOutline and MapFrame in its own signature: the
    // map is one component, and the projection it draws through has to be the
    // same one the arc is sampled into. :core:routing is pure JVM geometry with
    // no Android, no Compose and no data access, so this stays a dependency on
    // algorithms rather than on the app's data layer — which is the line this
    // module is not allowed to cross.
    api(projects.core.routing)

    // AircraftSilhouette and the route map's plane draw an AircraftShape, and a
    // caller resolves one through AircraftArt. :core:aircraftart is pure JVM — a
    // generated table of path strings and the rule that picks one — so this is
    // data the design system draws, not data it fetches.
    api(projects.core.aircraftart)

    // The route map's scene — its paths, widths, markers and draw order — and
    // the parsed aircraft outlines. They live below this module, in Compose
    // graphics with no Material, so the watch can draw the same map without the
    // phone's alpha-pinned material3. `implementation`, not `api`: RouteMap,
    // NetworkMap and the widget's masks take routing and aircraft types in their
    // signatures and never a scene, so no caller of this module names one.
    implementation(projects.core.mapscene)

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
