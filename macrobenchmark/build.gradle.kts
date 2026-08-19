plugins {
    alias(libs.plugins.flightplanner.android.benchmark)
    alias(libs.plugins.androidx.baselineprofile)
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

baselineProfile {
    // One physical device, the same one every other number in docs/UI-PLAN.md
    // comes from. A managed virtual device would generate a profile too, but a
    // profile is a claim about which code paths are hot, and an emulator's are
    // not this phone's.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.junit4)
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
