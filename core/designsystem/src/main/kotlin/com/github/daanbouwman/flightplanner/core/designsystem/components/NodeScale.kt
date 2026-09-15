package com.github.daanbouwman.flightplanner.core.designsystem.components

import kotlin.math.sqrt

/**
 * Where a visited airport's dot sits between the smallest and the largest dot
 * in its set, as a fraction `0..1` of the way from the minimum radius to the
 * maximum. The one sizing rule for the visited network, on the flat
 * [NetworkMap] and on the globe's node layer alike.
 *
 * ### Scaled from the least-visited field, not from zero
 *
 * The first version scaled `sqrt(visits / maxVisits)`. In a logbook where
 * every airport has been visited once — which is what a new logbook is —
 * `maxVisits` is 1, every dot is `sqrt(1)` of the way up, and *every* field is
 * drawn at the maximum. Seen on a device as two fields 90 NM apart, KOLD and
 * KVGC, merging into one 9 dp blob on both maps. Size was meant to say
 * "visited more than the others", and on that log nothing has been.
 *
 * So the scale runs from the set's own minimum: the least-visited field is
 * always the small dot, the most-visited always the large one, and a set where
 * every count is equal is all small dots — equal counts carry no information,
 * and the smallest mark is the honest one. Between them the fraction goes as
 * the square root of the count *above the floor*, so the dot's **area** grows
 * linearly with the visits a field has over the least-visited one; scaling the
 * radius linearly would make a field visited nine times more look nine times
 * the size, which is the bubble-chart error an eye reads as area whether or not
 * it was drawn as one.
 *
 * Public rather than `internal` because `:feature:globe` draws the same dots on
 * the sphere and must use the same rule; it is geometry, and this module knows
 * shapes.
 */
fun nodeSizeFraction(visits: Int, minVisits: Int, maxVisits: Int): Float {
    if (maxVisits <= minVisits) return 0f
    val above = (visits.coerceIn(minVisits, maxVisits) - minVisits).toFloat()
    return sqrt(above / (maxVisits - minVisits))
}
