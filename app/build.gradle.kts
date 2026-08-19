plugins {
    alias(libs.plugins.flightplanner.android.application)
    alias(libs.plugins.flightplanner.android.compose)
    alias(libs.plugins.flightplanner.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.github.daanbouwman.flightplanner"

    defaultConfig {
        applicationId = "com.github.daanbouwman.flightplanner"
        versionCode = 1
        versionName = "0.1.0"
    }

    androidResources {
        // The prebuilt index is read on every launch and its whole point is to be
        // fast, so store it uncompressed rather than paying an inflate each time.
        // The database is the opposite case: extracted once, so compression wins.
        noCompress += "index"
    }

    buildTypes {
        debug {
            // Filament and bundled SQLite ship native libraries for four ABIs,
            // which is ~32 MB of the debug APK. Local builds only ever run on a
            // real phone (arm64) or an emulator (x86_64), so drop the rest and
            // make install-and-iterate noticeably faster. Release keeps them all.
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
        release {
            // Debug-signed so that `installRelease` reaches a phone. Everything
            // else about this build type is real — minified, shrunk, not
            // debuggable, and running the committed baseline profile — so it is
            // what to install when the question is how the app actually feels.
            // Only the signature is local, which is exactly the part a store
            // would replace, so this **installs and must never be published**;
            // publishing needs a real key wired in here first.
            //
            // Before this, the only non-debuggable build that could be installed
            // was `benchmarkRelease`, whose name is the baseline-profile plugin's
            // and describes what it is for rather than what it is. Reaching for a
            // variant named after benchmarking to answer "how does this feel on my
            // phone" is how that question stops being asked.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // There is deliberately no hand-written `benchmark` build type here any
        // more. `androidx.baselineprofile` creates two of its own from `release`,
        // and the first is what that one was: `benchmarkRelease` is minified,
        // non-debuggable, debug-signed and — the part that used to need a
        // hand-written `<profileable>` manifest — profileable. `nonMinifiedRelease`
        // is the same minus minification, which is what a profile has to be
        // generated against if its rules are to name real classes rather than
        // `a.b.c`. Keeping a third, near-identical build type alongside them only
        // multiplied the test module's variants. See macrobenchmark/README.md.
    }
}

// The two build types above arrive from the plugin at `finalizeDsl`, which is
// after this script runs, so their ABI filters cannot be set in `buildTypes`.
// Without this they inherit `release`'s four ABIs — ~32 MB of Filament and
// bundled SQLite that no measurement uses, installed and uninstalled around
// every benchmark run.
androidComponents.finalizeDsl { extension ->
    listOf("benchmarkRelease", "nonMinifiedRelease").forEach { name ->
        extension.buildTypes.getByName(name).ndk.abiFilters += listOf("arm64-v8a", "x86_64")
    }
}

baselineProfile {
    // Generating a profile means installing two APKs, driving the app on a
    // physical device and pulling a file back — minutes, not seconds. It must not
    // happen because somebody ran `assembleRelease`. The profile is regenerated
    // deliberately, by `:app:generateBaselineProfile`, and committed.
    automaticGenerationDuringBuild = false

    // Every variant reads one profile from `src/main`, rather than a copy per
    // variant that can drift apart.
    mergeIntoMain = true
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.routing)
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(projects.core.designsystem)
    implementation(projects.feature.globe)

    // The producer of the baseline profile. `targetProjectPath` in
    // `:macrobenchmark` points the other way — that is how the test module finds
    // the app to measure — and the plugin needs both directions before
    // `generateBaselineProfile` does anything. Without this it succeeds, writes
    // nothing, and says so only in a warning.
    baselineProfile(projects.macrobenchmark)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.startup)
    // What actually applies the baseline profile on first run. It arrives
    // transitively through Compose as well, but a profile is inert without it,
    // and a load-bearing dependency should not rest on someone else's POM.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // The JUnit 5 variant by name: an Android module resolves plain `kotlin-test`
    // through a variant that maps to JUnit 4, where `kotlin.test.Test` is simply
    // absent. See the note in libs.versions.toml.
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
