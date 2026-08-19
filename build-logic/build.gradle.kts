import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.github.daanbouwman.flightplanner.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // compileOnly: these plugins are on the classpath of the *consuming* build,
    // not bundled into build-logic itself.
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    // Needed at compile time: AndroidRoomConventionPlugin references RoomExtension.
    compileOnly(libs.room.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "flightplanner.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "flightplanner.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "flightplanner.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "flightplanner.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "flightplanner.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("androidBenchmark") {
            id = "flightplanner.android.benchmark"
            implementationClass = "AndroidBenchmarkConventionPlugin"
        }
        register("jvmLibrary") {
            id = "flightplanner.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
