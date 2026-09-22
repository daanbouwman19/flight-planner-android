import javax.inject.Inject

plugins {
    alias(libs.plugins.flightplanner.android.application)
    alias(libs.plugins.flightplanner.android.hilt)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.github.daanbouwman.flightplanner.wear"

    defaultConfig {
        // The same application id as `:app`, which is how Play pairs a watch
        // APK with its phone APK. They are separate artifacts installed on
        // separate devices, so the shared id costs nothing here.
        applicationId = "com.github.daanbouwman.flightplanner"
        versionCode = 1
        versionName = "0.1.0"
    }

    // Compose is enabled here rather than by `flightplanner.android.compose`,
    // because that plugin adds `androidx.compose.material3` — the *phone*
    // Material library, pinned to an alpha for the Expressive surface, and a
    // dependency a watch APK must never carry. The watch's Material surface is
    // `androidx.wear.compose:compose-material3`, declared below.
    buildFeatures.compose = true

    androidResources {
        // Same reasoning as `:app`: the index is read on every launch and its
        // whole point is to be fast, so it is stored uncompressed rather than
        // paying an inflate each time. The outline is small enough not to care,
        // but it is decoded the same way and the rule is per-extension.
        noCompress += "index"
        noCompress += "outline"
    }

    buildTypes {
        release {
            // Debug-signed for the same reason `:app`'s release is: so it can be
            // installed on a real watch. It must never be published as it stands.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

/**
 * The watch's copy of the three assets it needs, taken from `:app`'s.
 *
 * A Wear APK is installed separately and carries its own assets, so the watch
 * needs its own copy — but a second copy *in the repository* is a 1.5 MB binary
 * that would silently drift from the one the ETL regenerates. Copying at build
 * time keeps one file under version control and one source of truth.
 *
 * Only three of the four things in `:app/src/main/assets` come across. The
 * 6.5 MB airport database does not: it carries names, municipalities,
 * elevations and runway detail, none of which this app shows, and route
 * generation needs only the index.
 *
 * This is a task type rather than a plain `Copy` because AGP's asset sources
 * are wired through [com.android.build.api.variant.Sources], and
 * `addGeneratedSourceDirectory` wires a task to its output by taking a
 * [DirectoryProperty] off it — which `Copy`, whose destination is a plain
 * `File`, does not have. Adding the directory to `sourceSets` instead is
 * refused outright by AGP 9: a `Provider` there carries no task dependency, so
 * the copy would race the merge.
 */
abstract class CopyWearAssets : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val phoneAssets: DirectoryProperty

    /**
     * Set by AGP, per variant, when this task is wired to the variant's asset
     * sources — so it is deliberately never assigned here. Two variants would
     * otherwise write over one another's output.
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun copy() {
        files.sync {
            from(phoneAssets) {
                include(
                    "databases/airports.index",
                    "maps/land.outline",
                    "seed/aircrafts.csv",
                )
            }
            into(outputDirectory)
        }
    }
}

androidComponents {
    onVariants { variant ->
        val copyWearAssets = tasks.register<CopyWearAssets>(
            "copy${variant.name.replaceFirstChar(Char::uppercase)}WearAssets",
        ) {
            description = "Copies the airport index, the seed fleet and the world outline from :app."
            phoneAssets.set(rootProject.layout.projectDirectory.dir("app/src/main/assets"))
        }

        val assets = checkNotNull(variant.sources.assets) {
            "The ${variant.name} variant has no asset sources, so the airport index cannot be bundled."
        }
        assets.addGeneratedSourceDirectory(copyWearAssets, CopyWearAssets::outputDirectory)
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.routing)
    implementation(projects.core.handoff)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Plain Compose, BOM-managed. The watch face below is a bespoke full-bleed
    // layout rather than a stack of list components, so most of what it needs is
    // foundation and the graphics layer; Wear Compose supplies the theme and the
    // typography that make it read as a watch app rather than a shrunken phone.
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.tooling.preview)
    implementation(libs.wear.remote.interactions)
    implementation(libs.play.services.wearable)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // The JUnit 5 variant by name: an Android module resolves plain `kotlin-test`
    // through a variant that maps to JUnit 4, where `kotlin.test.Test` is simply
    // absent. See `:core:network`'s build.gradle.kts for the same note.
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
