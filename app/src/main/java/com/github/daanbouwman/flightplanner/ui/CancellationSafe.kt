package com.github.daanbouwman.flightplanner.ui

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] that lets cancellation through.
 *
 * `runCatching` catches `Throwable`, which includes `CancellationException`, and
 * swallowing that quietly breaks structured concurrency: a cancelled load would
 * be reported to the user as a failure, and the coroutine would carry on running
 * code after the point it was told to stop.
 *
 * One copy, `internal` to `:app`. It used to be a file-private function in eight
 * files — every ViewModel that touched a repository, plus the search and the
 * route-detail loader — each with a KDoc pointing at `PlanViewModel`'s copy for
 * the reasoning. Eight identical private functions are not a pattern, they are a
 * helper nobody got round to sharing; the first divergence between them would
 * have been a bug that only one screen had.
 */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}
