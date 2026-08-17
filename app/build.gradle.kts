plugins {
    alias(libs.plugins.flightplanner.android.application)
    alias(libs.plugins.flightplanner.android.compose)
    alias(libs.plugins.flightplanner.android.hilt)
    alias(libs.plugins.kotlin.serialization)
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("benchmark") {
            // A release build that can be installed without release keys.
            //
            // This exists because a debug APK is `debuggable`, and a debuggable
            // process runs largely interpreted: measured here, fifty short-range
            // routes cost 110 ms on device against 2 ms on a warm JVM, entirely
            // because ART never compiles the code. Any startup or throughput
            // number taken from a debug build is measuring the debugger, not the
            // app, so timings are only meaningful on this variant.
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.routing)
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(projects.core.designsystem)
    implementation(projects.feature.globe)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.startup)
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
