plugins {
    alias(libs.plugins.flightplanner.android.benchmark)
}

android {
    namespace = "com.github.daanbouwman.flightplanner.macrobenchmark"

    // The app under measurement. Only its `benchmark` variant is ever built from
    // here — see the convention plugin for why there is no debug counterpart.
    targetProjectPath = ":app"

    defaultConfig {
        // Four ABIs of Filament and bundled SQLite are ~32 MB the measurement
        // does not need. This has to agree with `:app`'s benchmark build type or
        // the two APKs disagree about which libraries exist.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.junit4)
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
