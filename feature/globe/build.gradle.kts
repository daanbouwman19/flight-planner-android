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
// old shader with nothing to say so.
//
// verifyFilamatFreshness catches the *forgotten* case: it hashes each pair against
// src/main/materials/checksums.txt and fails `check` on any drift. On its own that
// is only file identity, not proof that the blob was compiled from the source next
// to it — so updateFilamatChecksums refuses to bless a pair whose .mat changed
// while its .filamat did not, which is exactly the shape of "I forgot to run matc,
// then regenerated the manifest". A real matc run on a changed source produces a
// different blob; a comment-only edit that genuinely compiles to the same bytes is
// the one false positive, and -PallowUnchangedFilamat=true is its escape hatch.
//
// The second thing it catches is a *runtime* bump. A .filamat opens with a
// MAT_VERSION chunk, and Material.Builder.build() aborts the process when it is
// not the version the linked libfilament expects — Dependabot's 1.75 -> 1.76.1
// bump shipped with blobs still at 75 and the globe crashed on open with
// "Material version mismatch. Expected 76 but received 75". Neither hash moves in
// that case, so the manifest also records the Filament release whose matc built
// the blobs (`matc  1.76.1`), and verifyFilamatFreshness fails when that is not
// the version the catalog pins. Every runtime bump therefore costs a recompile,
// even one whose material version happens not to change; a recompile is always
// safe and the alternative is a crash that only a device can show.
val materialsDirFile: File = layout.projectDirectory.dir("src/main/materials").asFile
val filamatDirFile: File = layout.projectDirectory.dir("src/main/assets/materials").asFile
val filamentVersion: String = libs.versions.filament.get()

/** The parsed manifest: the `matc` release line, and one (matHash, filamatHash) per material. */
data class FilamatManifest(val matc: String?, val entries: Map<String, Pair<String, String>>) {
    // A companion rather than a script-level function: a task action calling a
    // script function captures the script object, which the configuration cache
    // refuses to serialise. A companion call is a static access and captures nothing.
    companion object {
        fun read(file: File): FilamatManifest {
            var matc: String? = null
            val entries = mutableMapOf<String, Pair<String, String>>()
            file.readLines()
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .forEach { line ->
                    val parts = line.split(Regex("\\s+"))
                    when {
                        parts.size == 2 && parts[0] == "matc" -> matc = parts[1]
                        parts.size == 3 -> entries[parts[0]] = parts[1] to parts[2]
                        else -> throw GradleException("malformed checksums.txt line: \"$line\"")
                    }
                }
            return FilamatManifest(matc, entries)
        }
    }
}

val verifyFilamatFreshness = tasks.register("verifyFilamatFreshness") {
    description = "Fails if a Filament .mat source no longer matches its committed .filamat."
    group = "verification"

    val materials = materialsDirFile
    val filamats = filamatDirFile
    val runtime = filamentVersion
    inputs.dir(materials)
    inputs.dir(filamats)
    inputs.property("filamentVersion", runtime)

    doLast {
        fun sha256(file: File): String =
            MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }

        val (matc, manifest) = FilamatManifest.read(File(materials, "checksums.txt"))

        val matNames = materials.listFiles { f -> f.extension == "mat" }
            .orEmpty().map { it.nameWithoutExtension }.sorted()
        val problems = mutableListOf<String>()

        when (matc) {
            null -> problems += "checksums.txt has no \"matc <version>\" line"
            runtime -> Unit
            else -> problems +=
                "the .filamat files were compiled by matc $matc but libs.versions.toml " +
                    "pins filament-android $runtime; the runtime will refuse the old material " +
                    "format and abort the process"
        }

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
                    appendLine("Recompile with matc from the Filament v$runtime release archive")
                    appendLine("(https://github.com/google/filament/releases), then:")
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
    val runtime = filamentVersion
    // Read at configuration time: touching `providers` inside the task action
    // captures the Project, which the configuration cache refuses to serialise.
    val allowUnchanged = providers.gradleProperty("allowUnchangedFilamat")
        .map { it.toBoolean() }.getOrElse(false)

    doLast {
        fun sha256(file: File): String =
            MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }

        val manifestFile = File(materials, "checksums.txt")
        val recorded = FilamatManifest.read(manifestFile).entries

        val header = manifestFile.readLines().takeWhile { it.startsWith("#") || it.isBlank() }
        // The blobs are taken to have come from the matc that matches the pinned
        // runtime: that is the only matc whose output the runtime is known to
        // load, and the task cannot tell which binary was run.
        val matcLine = "matc  $runtime"
        val stale = mutableListOf<String>()
        val lines = materials.listFiles { f -> f.extension == "mat" }
            .orEmpty().sortedBy { it.name }.map { mat ->
                val name = mat.nameWithoutExtension
                val filamat = File(filamats, "$name.filamat")
                require(filamat.isFile) { "no compiled $name.filamat to hash" }
                val matHash = sha256(mat)
                val filamatHash = sha256(filamat)
                val was = recorded[name]
                // The source moved and the blob did not: that is a forgotten
                // matc run, and blessing it would make the guard permanently
                // blind to this material.
                if (was != null && was.first != matHash && was.second == filamatHash) {
                    stale += name
                }
                "$name  $matHash  $filamatHash"
            }

        if (stale.isNotEmpty() && !allowUnchanged) {
            throw GradleException(
                buildString {
                    appendLine("Refusing to record a .mat change with an unchanged .filamat:")
                    stale.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("Recompile it first:")
                    stale.forEach {
                        appendLine(
                            "  matc --platform=mobile --api=opengl --api=vulkan " +
                                "-o feature/globe/src/main/assets/materials/$it.filamat " +
                                "feature/globe/src/main/materials/$it.mat",
                        )
                    }
                    appendLine()
                    append(
                        "If the edit genuinely compiles to identical bytes (a comment, say), " +
                            "re-run with -PallowUnchangedFilamat=true.",
                    )
                },
            )
        }
        manifestFile.writeText((header + matcLine + lines).joinToString("\n") + "\n")
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
    // `trace(...)` sections around the frame callback's stages, so
    // `:macrobenchmark`'s GlobeSpinBenchmark can read each one's cost off a
    // Perfetto trace. Free when nothing is tracing: one `isEnabled` check.
    implementation(libs.androidx.tracing.ktx)

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
