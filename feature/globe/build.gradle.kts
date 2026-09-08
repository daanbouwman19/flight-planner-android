import java.io.File
import java.security.MessageDigest
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

// NOTE: line comments, not a KDoc block, are deliberate here — a `/** */` block
// containing a fenced code sample silently truncated Gradle's Kotlin DSL script
// evaluation (tasks below this point simply did not register, with no error).
//
// The hand-compiled Filament materials, and the guard that keeps them honest.
//
// src/main/materials/*.mat are compiled to src/main/assets/materials/*.filamat by
// hand with:
//   matc --platform=mobile --api=opengl --api=vulkan
//     -o src/main/assets/materials/<name>.filamat src/main/materials/<name>.mat
// matc ships only in the Filament release archive, not in filament-android and not
// on any developer machine by default, so there is no Gradle task that can
// recompile them. A .mat edited without its .filamat regenerated would ship the
// old shader with nothing to say so. verifyFilamatFreshness closes that: it hashes
// each pair against src/main/materials/checksums.txt and fails `check` on any
// drift. After a legitimate recompile, run updateFilamatChecksums and commit the
// manifest alongside the new blob.
val materialsDirFile: File = layout.projectDirectory.dir("src/main/materials").asFile
val filamatDirFile: File = layout.projectDirectory.dir("src/main/assets/materials").asFile

val verifyFilamatFreshness = tasks.register("verifyFilamatFreshness") {
    description = "Fails if a Filament .mat source no longer matches its committed .filamat."
    group = "verification"

    val materials = materialsDirFile
    val filamats = filamatDirFile
    inputs.dir(materials)
    inputs.dir(filamats)

    doLast {
        fun sha256(file: File): String =
            MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }

        val manifest: Map<String, Pair<String, String>> =
            File(materials, "checksums.txt").readLines()
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .associate { line ->
                    val parts = line.split(Regex("\\s+"))
                    require(parts.size == 3) { "malformed checksums.txt line: \"$line\"" }
                    parts[0] to (parts[1] to parts[2])
                }

        val matNames = materials.listFiles { f -> f.extension == "mat" }
            .orEmpty().map { it.nameWithoutExtension }.sorted()
        val problems = mutableListOf<String>()

        for (name in matNames) {
            val mat = File(materials, "$name.mat")
            val filamat = File(filamats, "$name.filamat")
            val recorded = manifest[name]
            when {
                recorded == null ->
                    problems += "$name.mat has no line in checksums.txt"
                !filamat.isFile ->
                    problems += "$name.mat has no compiled $name.filamat"
                sha256(mat) != recorded.first ->
                    problems += "$name.mat changed but $name.filamat was not recompiled"
                sha256(filamat) != recorded.second ->
                    problems += "$name.filamat does not match the checksums.txt hash"
            }
        }
        (manifest.keys - matNames.toSet()).forEach {
            problems += "checksums.txt lists \"$it\" but src/main/materials/$it.mat is gone"
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Filament materials are out of sync:")
                    problems.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("Recompile with matc, then:")
                    appendLine("  ./gradlew :feature:globe:updateFilamatChecksums")
                    append("and commit checksums.txt with the regenerated .filamat.")
                },
            )
        }
    }
}

tasks.register("updateFilamatChecksums") {
    description = "Rewrites src/main/materials/checksums.txt from the current .mat/.filamat files."
    group = "build"

    val materials = materialsDirFile
    val filamats = filamatDirFile

    doLast {
        fun sha256(file: File): String =
            MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }

        val manifestFile = File(materials, "checksums.txt")
        val header = manifestFile.readLines().takeWhile { it.startsWith("#") || it.isBlank() }
        val lines = materials.listFiles { f -> f.extension == "mat" }
            .orEmpty().sortedBy { it.name }.map { mat ->
                val name = mat.nameWithoutExtension
                val filamat = File(filamats, "$name.filamat")
                require(filamat.isFile) { "no compiled $name.filamat to hash" }
                "$name  ${sha256(mat)}  ${sha256(filamat)}"
            }
        manifestFile.writeText((header + lines).joinToString("\n") + "\n")
        logger.lifecycle("Wrote src/main/materials/checksums.txt")
    }
}

tasks.named("check") { dependsOn(verifyFilamatFreshness) }

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
