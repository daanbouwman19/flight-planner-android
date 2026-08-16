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

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
