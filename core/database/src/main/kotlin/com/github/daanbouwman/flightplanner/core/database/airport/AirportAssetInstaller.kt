package com.github.daanbouwman.flightplanner.core.database.airport

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AirportAssetInstaller"

/** Where the one process-wide install of the airport database has got to. */
sealed interface InstallState {

    /** Nothing has asked for the database yet and the warm-up has not run. */
    data object Idle : InstallState

    /** The asset is being checked against the copy on disk, or copied out. */
    data object Installing : InstallState

    /** The on-disk database is in place and matches the shipped asset. */
    data class Ready(val file: File) : InstallState

    data class Failed(val cause: Throwable) : InstallState

    /**
     * True once the outcome is known either way. The splash screen waits on this
     * rather than on [Ready]: a failed install must show the app (and let the
     * self-check screen report it) instead of holding the splash forever.
     */
    val settled: Boolean get() = this is Ready || this is Failed
}

/**
 * Where the installer reads the shipped files from — `Context.assets` in the
 * app, a map of streams in a test. A seam, not an abstraction anybody else uses.
 */
fun interface AssetOpener {
    /** @throws java.io.IOException when there is no such asset. */
    fun open(path: String): InputStream
}

/**
 * Copies the shipped airport database out of the APK.
 *
 * Room has a `createFromAsset` helper that would do this, but it throws
 * `Pre-Package Database is not supported when an SQLiteDriver is configured`
 * when combined with `BundledSQLiteDriver`. Since the bundled driver is the
 * whole reason this app behaves identically across OEM SQLite builds, the copy
 * is done here instead and Room simply opens the resulting file.
 *
 * Freshness is decided by a version sidecar written by the ETL, which holds
 * Room's schema identity hash. That single value changes whenever the entities
 * change *or* the dataset is regenerated, so a stale copy can never survive
 * either — which matters, because Room's own reaction to a schema mismatch is
 * to drop every table and hand the app an empty database.
 *
 * ### Off the main thread, but complete before the first query
 *
 * The copy has the same shape as the index build in `DefaultAirportIndexProvider`
 * and for the same reason: it is started lazily, [warm]ed from
 * `Application.onCreate` onto an IO dispatcher, and awaited by whoever needs
 * the result — normally long after it has finished. What is different is *who*
 * awaits. Room is not using `createFromAsset`, so if any DAO query ran before
 * the file existed Room would silently create an empty, schema-matching
 * database and the app would run with no airports and no error. The copy must
 * therefore be complete before the first query, not merely before the
 * provider hands out the database — which is why `DatabaseModule` calls
 * [ensureInstalledBlocking] rather than something that suspends. On the common
 * path that call finds the install already settled and returns at once; it
 * blocks only for the remainder of a copy that is still running, and it never
 * starts a second one.
 *
 * In practice it never blocks at all, because the launch splash waits for
 * [isSettled] *without* the deadline it puts on the index and the settings
 * (`splashShouldHold` in `:app`). The first version capped the install with the
 * same deadline, and on a first launch the ~30 MB copy outlived it: the app
 * appeared, Plan composed, and this call ran `runBlocking` on the main thread
 * for the rest of the copy behind a half-drawn screen — the original defect
 * narrowed to first launch rather than removed. A splash that lasts the copy is
 * the honest version of the same wait, and it cannot last forever:
 * [InstallState.Failed] counts as settled, so a corrupt or missing asset
 * releases the splash and lets the self-check screen report it.
 *
 * ### The failure is replaceable
 *
 * A `Deferred` caches its failure forever, so one transient I/O error would
 * otherwise disable the database for the life of the process. The current
 * install is held in an `AtomicReference` and [retry] swaps a failed one for a
 * fresh build, compare-and-set so two taps on a retry button are safe.
 */
@Singleton
class AirportAssetInstaller internal constructor(
    private val assets: AssetOpener,
    /** Resolved lazily so constructing the installer touches no filesystem. */
    private val databaseDir: () -> File,
    /**
     * Owned rather than injected, like `DefaultAirportRepository`'s: this
     * module has no application-scoped `CoroutineScope` binding, and the one
     * job the scope runs is a fire-and-forget copy whose result lands in [state].
     * A `SupervisorJob` keeps a failed install from cancelling the scope, so a
     * retry is possible.
     */
    private val scope: CoroutineScope,
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        assets = AssetOpener { path -> context.assets.open(path) },
        databaseDir = { checkNotNull(context.getDatabasePath(AirportDatabase.NAME).parentFile) },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)

    /** Observable progress, for the splash gate and the self-check screen. */
    val state: StateFlow<InstallState> = _state.asStateFlow()

    private val current = AtomicReference(newInstall())

    private fun newInstall(): Deferred<File> =
        scope.async(start = CoroutineStart.LAZY) {
            _state.value = InstallState.Installing
            try {
                val file = installBlocking()
                _state.value = InstallState.Ready(file)
                file
            } catch (cancellation: CancellationException) {
                // The scope outlives every screen, so this only happens at
                // process teardown. It is not an install failure.
                _state.value = InstallState.Idle
                throw cancellation
            } catch (failure: Throwable) {
                // Logged as well as surfaced: without the database most of the
                // app is empty, and a silent failure leaves nothing to diagnose.
                Log.e(TAG, "Installing the airport database failed", failure)
                _state.value = InstallState.Failed(failure)
                throw failure
            }
        }

    /**
     * Starts the install without waiting for it. Safe to call more than once;
     * `Deferred.start()` is a no-op once the job is running.
     */
    fun warm() {
        current.get().start()
    }

    /** Suspends until the database is installed, starting the install if it has not been. */
    suspend fun awaitInstalled(): File = current.get().await()

    /**
     * Blocks the calling thread until the database is installed.
     *
     * **For `DatabaseModule.provideAirportDatabase` only.** Room's builder is
     * synchronous and Hilt's provision is synchronous, and the file has to exist
     * before Room opens it — see the class KDoc for why. Once [warm] has
     * completed this is a completed `Deferred` being awaited, which returns
     * without suspending or blocking.
     */
    fun ensureInstalledBlocking(): File = runBlocking { awaitInstalled() }

    /**
     * Discards a failed install so the next [warm] or [awaitInstalled] tries again.
     *
     * A no-op unless the current state is [InstallState.Failed] — retrying a
     * succeeded or in-flight install would duplicate work.
     */
    fun retry() {
        if (_state.value !is InstallState.Failed) return
        val failed = current.get()
        if (!failed.isCompleted) return
        if (current.compareAndSet(failed, newInstall())) {
            _state.value = InstallState.Idle
            current.get().start()
        }
    }

    /** True once the install has either succeeded or failed. */
    val isSettled: Boolean get() = _state.value.settled

    /** The copy itself. Runs on the scope's dispatcher; the only thing here that touches disk. */
    private fun installBlocking(): File {
        val target = File(databaseDir(), AirportDatabase.NAME)
        val marker = File(target.parentFile, "${AirportDatabase.NAME}.version")
        val expected = readAssetVersion()

        if (target.exists() && expected != null && marker.readTextOrNull() == expected) {
            return target
        }

        Log.i(TAG, "Installing airport database (version $expected)")
        target.parentFile?.mkdirs()

        // Remove the old database and any journal sidecars; leaving a -wal file
        // beside a replaced database resurrects pages from the previous one.
        for (path in listOf(target.path, "${target.path}-wal", "${target.path}-shm", "${target.path}-journal")) {
            File(path).delete()
        }

        // Copy via a temporary file so an interrupted install cannot leave a
        // truncated database that looks complete on the next launch.
        val staging = File(target.parentFile, "${AirportDatabase.NAME}.tmp")
        staging.delete()
        assets.open(AirportDatabase.ASSET_PATH).use { input ->
            staging.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE * 8) }
        }
        check(staging.renameTo(target)) { "Could not move the airport database into place" }

        if (expected != null) marker.writeText(expected) else marker.delete()
        Log.i(TAG, "Installed ${target.length()} bytes")
        return target
    }

    /**
     * The identity hash the ETL wrote beside the asset, or null without one.
     *
     * At ERROR, not WARN: a missing sidecar means the ETL output is incomplete,
     * and its consequence — a full copy of the database on *every* launch — is
     * a defect in the build, not a condition to warn about and carry on.
     */
    private fun readAssetVersion(): String? = runCatching {
        assets.open(AirportDatabase.ASSET_VERSION_PATH).bufferedReader().use { it.readText().trim() }
    }.getOrElse {
        Log.e(TAG, "No version sidecar beside the airport database; it will be reinstalled on every launch", it)
        null
    }

    private fun File.readTextOrNull(): String? = runCatching { readText().trim() }.getOrNull()
}
