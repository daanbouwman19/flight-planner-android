package com.github.daanbouwman.flightplanner.aircraftart

import com.github.daanbouwman.flightplanner.model.AircraftSpec

/**
 * Which outline an airframe is drawn with.
 *
 * In order, the first that answers wins:
 *
 * 1. **The seed row itself**, by manufacturer and variant. Every bundled row
 *    has an outline of its own, so this is the whole story for an unedited
 *    fleet — including the rows an ICAO code cannot tell apart, like an A320
 *    with sharklets and one without.
 * 2. **The ICAO type designator.** An airframe added by hand as "B738" gets the
 *    737-800, whatever it was called. Where several seed rows share a code,
 *    the default is the first of them in the seed file.
 * 3. **The category**, as free text matched without regard to case: a
 *    hand-added "Narrow-body" with an unknown code gets a generic twin jet.
 * 4. **A generic single-engine piston**, the commonest thing a hand-added
 *    aircraft turns out to be.
 *
 * Nothing here touches the table until it is first asked, and the table holds
 * strings, not parsed paths: parsing belongs to the renderer and is cached
 * there.
 */
object AircraftArt {

    fun shapeFor(spec: AircraftSpec): AircraftShape =
        shapeFor(spec.manufacturer, spec.variant, spec.icaoCode, spec.category)

    fun shapeFor(manufacturer: String, variant: String, icaoCode: String, category: String): AircraftShape {
        val id = AircraftShapes.rows[rowKey(manufacturer, variant)]
            ?: AircraftShapes.icaoDefaults[icaoCode.trim().uppercase()]
            ?: AircraftShapes.categories[category.trim().lowercase()]
            ?: AircraftShapes.LAST_RESORT
        return byId.getValue(id)
    }

    /** The outline with this [id], or null if there is none. */
    fun byId(id: String): AircraftShape? = byId[id]

    /** Every outline, in manifest order. */
    val all: List<AircraftShape> get() = AircraftShapes.all

    private val byId: Map<String, AircraftShape> by lazy { AircraftShapes.all.associateBy { it.id } }

    internal fun rowKey(manufacturer: String, variant: String) = "${manufacturer.trim()}|${variant.trim()}"
}
