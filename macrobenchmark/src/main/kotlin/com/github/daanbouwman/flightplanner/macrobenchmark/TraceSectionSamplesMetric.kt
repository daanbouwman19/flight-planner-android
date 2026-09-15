package com.github.daanbouwman.flightplanner.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.ExperimentalTraceProcessorApi
import androidx.benchmark.traceprocessor.TraceProcessor

/**
 * Every occurrence of one trace section, as a distribution rather than a sum.
 *
 * ### Why not `TraceSectionMetric`
 *
 * The library's own section metric reduces an iteration to one number — the
 * sum, the average, the maximum, the count. The question the globe's frame
 * loop is judged on is a percentile: whether traversal plus mesh rebuild stays
 * under a few milliseconds at P90, because the frame that overruns is the one a
 * user sees, and an average over a second of spinning hides it. Handing the
 * library every occurrence as *samples* is what makes it report P50, P90, P95
 * and P99 for the section, the same way `FrameTimingMetric` reports frame
 * durations.
 *
 * The section is matched only inside the target process, so a same-named
 * section in another app cannot leak in; the names here are prefixed `globe:`
 * and nothing else on the device uses them, but the join costs nothing.
 *
 * @param sectionName the exact `trace(...)` label in the app.
 * @param measurementName what the result is reported as; conventionally ends in
 *   `Ms`, matching the library's own metrics.
 */
@OptIn(ExperimentalMetricApi::class, ExperimentalTraceProcessorApi::class)
class TraceSectionSamplesMetric(
    private val sectionName: String,
    private val measurementName: String,
) : TraceMetric() {

    override fun getMeasurements(
        captureInfo: CaptureInfo,
        traceSession: TraceProcessor.Session,
    ): List<Measurement> {
        val samplesMs = traceSession.query(
            """
            SELECT slice.dur AS dur
            FROM slice
            JOIN thread_track ON slice.track_id = thread_track.id
            JOIN thread USING (utid)
            JOIN process USING (upid)
            WHERE slice.name = '$sectionName'
              AND slice.dur > 0
              AND process.name LIKE '${captureInfo.targetPackageName}%'
            """.trimIndent(),
        ).map { it.long("dur") / NANOS_PER_MILLI }.toList()

        // An iteration in which the section never ran is a broken journey, not
        // a fast one: reporting it as zero would pull every percentile down.
        if (samplesMs.isEmpty()) {
            error("No '$sectionName' sections in the trace; the journey never reached the globe's frame loop")
        }
        return listOf(Measurement(measurementName, samplesMs))
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
