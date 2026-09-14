package com.github.daanbouwman.flightplanner.core.database.airport

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * A fake APK: the asset paths the installer reads, as bytes.
 *
 * Counts every open so a test can tell "checked the sidecar and left the file
 * alone" from "copied the whole database again".
 */
private class FakeAssets(
    private val files: MutableMap<String, ByteArray>,
    /** When set, every open of the database asset throws it — a corrupt APK read. */
    var databaseFailure: IOException? = null,
) : AssetOpener {
    var databaseOpens = 0
        private set

    override fun open(path: String): InputStream {
        if (path == AirportDatabase.ASSET_PATH) {
            databaseOpens++
            databaseFailure?.let { throw it }
        }
        val bytes = files[path] ?: throw FileNotFoundException(path)
        return ByteArrayInputStream(bytes)
    }
}

private const val VERSION_A = "e6a0be22a02ff6b1f2d0b604e22c4066"
private const val VERSION_B = "0123456789abcdef0123456789abcdef"

/**
 * The installer's state machine, against a temp directory and a fake APK.
 *
 * Plain JVM: `android.util.Log` is on the stub android.jar, which the library
 * convention plugin configures to return defaults rather than throw, so the
 * logging lines are inert here. No Robolectric — nothing in this class needs a
 * `Context` once the two seams (`AssetOpener`, the database directory) are
 * handed in directly.
 */
class AirportAssetInstallerTest {

    private val dir: File = Files.createTempDirectory("airport-installer").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun assets(
        database: ByteArray = "sqlite bytes A".toByteArray(),
        version: String? = VERSION_A,
    ) = FakeAssets(
        buildMap {
            put(AirportDatabase.ASSET_PATH, database)
            if (version != null) put(AirportDatabase.ASSET_VERSION_PATH, version.toByteArray())
        }.toMutableMap(),
    )

    private fun installer(assets: AssetOpener) = AirportAssetInstaller(assets, { dir }, scope)

    private val target get() = File(dir, AirportDatabase.NAME)
    private val marker get() = File(dir, "${AirportDatabase.NAME}.version")

    @AfterTest
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun `constructing the installer touches nothing, warm starts the copy, await sees it ready`() = runTest {
        val fake = assets()
        val installer = installer(fake)

        installer.state.value shouldBe InstallState.Idle
        installer.isSettled shouldBe false
        fake.databaseOpens shouldBe 0
        target.exists() shouldBe false

        installer.warm()
        val file = installer.awaitInstalled()

        file shouldBe target
        installer.state.value shouldBe InstallState.Ready(target)
        installer.isSettled shouldBe true
        target.readText() shouldBe "sqlite bytes A"
        marker.readText() shouldBe VERSION_A
        fake.databaseOpens shouldBe 1
    }

    @Test
    fun `awaiting without warming starts the install itself`() = runTest {
        val installer = installer(assets())
        installer.awaitInstalled() shouldBe target
        installer.state.value shouldBe InstallState.Ready(target)
    }

    @Test
    fun `a second warm, a second await and the blocking wait are all the one install`() = runTest {
        val fake = assets()
        val installer = installer(fake)

        installer.warm()
        installer.warm()
        installer.awaitInstalled()
        installer.awaitInstalled()
        installer.ensureInstalledBlocking() shouldBe target

        fake.databaseOpens shouldBe 1
    }

    @Test
    fun `a copy whose sidecar matches the asset is left alone on the next launch`() = runTest {
        val first = assets()
        installer(first).awaitInstalled()
        first.databaseOpens shouldBe 1

        // A second process: a fresh installer over the same directory.
        val second = assets()
        installer(second).awaitInstalled()

        second.databaseOpens shouldBe 0
        target.readText() shouldBe "sqlite bytes A"
    }

    @Test
    fun `a new asset version replaces the copy and its journal sidecars`() = runTest {
        installer(assets()).awaitInstalled()
        File(dir, "${AirportDatabase.NAME}-wal").writeText("stale pages")
        File(dir, "${AirportDatabase.NAME}-shm").writeText("stale index")

        val updated = assets(database = "sqlite bytes B".toByteArray(), version = VERSION_B)
        installer(updated).awaitInstalled()

        updated.databaseOpens shouldBe 1
        target.readText() shouldBe "sqlite bytes B"
        marker.readText() shouldBe VERSION_B
        File(dir, "${AirportDatabase.NAME}-wal").exists() shouldBe false
        File(dir, "${AirportDatabase.NAME}-shm").exists() shouldBe false
    }

    @Test
    fun `without a version sidecar the database is reinstalled on every launch`() = runTest {
        // The ERROR log this path emits is inert on the stub android.jar, so
        // what is asserted is its consequence: no marker, and a full copy each
        // time — which is exactly why it is logged at ERROR and not WARN.
        val first = assets(version = null)
        installer(first).awaitInstalled()
        first.databaseOpens shouldBe 1
        marker.exists() shouldBe false

        val second = assets(version = null)
        installer(second).awaitInstalled()
        second.databaseOpens shouldBe 1
    }

    @Test
    fun `a failed copy is Failed, leaves no half-written database, and retry tries again`() = runTest {
        val fake = assets().apply { databaseFailure = IOException("asset stream reset") }
        val installer = installer(fake)

        installer.warm()
        shouldThrow<IOException> { installer.awaitInstalled() }

        installer.state.value.shouldBeInstanceOf<InstallState.Failed>().cause.message shouldBe "asset stream reset"
        installer.isSettled shouldBe true
        // The copy went through a staging file; a failure must not leave the
        // real name behind for Room to open as an empty database.
        target.exists() shouldBe false

        fake.databaseFailure = null
        installer.retry()
        installer.awaitInstalled() shouldBe target

        installer.state.value shouldBe InstallState.Ready(target)
        target.readText() shouldBe "sqlite bytes A"
        fake.databaseOpens shouldBe 2
    }

    @Test
    fun `retry is a no-op unless the install has failed`() = runTest {
        val fake = assets()
        val installer = installer(fake)

        // Idle: nothing to retry, and nothing is started.
        installer.retry()
        installer.state.value shouldBe InstallState.Idle
        fake.databaseOpens shouldBe 0

        // Ready: a retry must not throw away a good copy or copy again.
        installer.awaitInstalled()
        installer.retry()
        installer.state.value shouldBe InstallState.Ready(target)
        fake.databaseOpens shouldBe 1
    }

    @Test
    fun `the blocking wait surfaces a failed install as the exception, not a hang`() {
        val fake = assets().apply { databaseFailure = IOException("no such asset") }
        val installer = installer(fake)

        shouldThrow<IOException> { installer.ensureInstalledBlocking() }
        installer.state.value.shouldBeInstanceOf<InstallState.Failed>()
    }
}
