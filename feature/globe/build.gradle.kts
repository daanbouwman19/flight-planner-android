import java.util.Properties

plugins {
    alias(libs.plugins.flightplanner.android.library)
    alias(libs.plugins.flightplanner.android.compose)
}

/**
 * The ArcGIS Location Platform API key the imagery provider is keyed with, or
 * "" for a clone that has none — in which case the globe falls back to NASA GIBS
 * and still builds and still draws.
 *
 * Deliberately **not** a committed constant: the key is a credential tied to one
 * developer's account and its usage quota. Put it in `local.properties` as
 * `arcgis.apiKey=...` (that file is gitignored), or pass it as the Gradle
 * property of the same name, or export `ARCGIS_API_KEY` — read in that order.
 * `local.properties` is read with `java.util.Properties` rather than an
 * AGP-internal helper, because those move between AGP majors and this one line
 * does not need to.
 */
val arcgisApiKey: String = run {
    val local = Properties()
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(local::load)
    providers.gradleProperty("arcgis.apiKey").orNull
        ?: local.getProperty("arcgis.apiKey")
        ?: providers.environmentVariable("ARCGIS_API_KEY").orNull
        ?: ""
}

android {
    namespace = "com.github.daanbouwman.flightplanner.feature.globe"

    // Off by default in AGP 9; on here for exactly one field.
    buildFeatures.buildConfig = true

    defaultConfig {
        // The value becomes a Java string literal, so it is escaped as one.
        val literal = arcgisApiKey.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "ARCGIS_API_KEY", "\"$literal\"")
    }
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.designsystem)

    // Filament: Kotlin API over a Vulkan backend. Prebuilt native libraries ship
    // in the AAR, so no C/C++ is written or compiled here.
    //
    // Not `filament-utils-android`: that AAR is `com.google.android.filament.utils`
    // (camera helpers, a glTF loader, the KTX1 texture path), and this module
    // uses none of it — the camera math is ported by hand in `math/`, imagery
    // comes from tile bitmaps, and nothing here loads a model. It was declared
    // and never imported; ~4.3 MB of dead native code per ABI.
    implementation(libs.filament.android)

    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.process)

    // The JUnit 5 variant by name: an Android module resolves plain `kotlin-test`
    // through a variant that maps to JUnit 4, where `kotlin.test.Test` is simply
    // absent. See the note in libs.versions.toml.
    testImplementation(libs.kotlin.test.junit5)

    // The gesture pump lives inside `awaitPointerEventScope` and cannot be driven
    // from a JVM test; the pinch and the drag are proved on a device instead,
    // against a real surface. See GlobePinchTest.
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    // Pinned above what ui-test-junit4 pulls in transitively: the older Espresso
    // idles through reflection on `InputManager.getInstance`, which Android 16
    // removed, and every test fails in `onIdle` before it touches the globe.
    androidTestImplementation(libs.androidx.test.espresso)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    // A real HTTP stack under the tile loader's tests: the cache policy and the
    // offline fallback are properties of the wire, not of a fake.
    testImplementation(libs.okhttp.mockwebserver3)
}
