package com.github.daanbouwman.flightplanner.mapscene

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.PathParser
import com.github.daanbouwman.flightplanner.aircraftart.AircraftShape
import java.util.concurrent.ConcurrentHashMap

/**
 * The parsed outline of [shape], in centimetres with its origin at the top left
 * of the bounding box.
 *
 * Parsed once per outline per process and shared: a Fleet list of 133 rows, a
 * Plan list, the widget's renderer and every page of the watch's feed draw the
 * same few dozen paths, and parsing a 1–3 kB path string on every bind is
 * avoidable work on the main thread. Safe to call from any thread — the scene
 * builder runs on a background dispatcher on the watch.
 *
 * **Never mutate the returned path.** It is the one instance every caller in the
 * process shares; callers transform the canvas instead, as [drawRouteMap] does.
 * Public because `:core:designsystem`'s `AircraftSilhouette` draws the same
 * cached path the route map places.
 */
fun silhouettePath(shape: AircraftShape): Path = SilhouettePaths.computeIfAbsent(shape.id) {
    PathParser().parsePathString(shape.pathData).toPath().apply {
        // The generator's contract (see AircraftShape): rings are filled
        // even-odd, so a hole is a hole whichever way it winds.
        fillType = PathFillType.EvenOdd
    }
}

private val SilhouettePaths = ConcurrentHashMap<String, Path>()
