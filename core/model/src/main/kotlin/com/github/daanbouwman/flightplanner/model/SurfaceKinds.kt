package com.github.daanbouwman.flightplanner.model

/**
 * Normalisation of the free-text OurAirports `surface` column.
 *
 * The upstream data has no controlled vocabulary; the same surface appears as
 * `ASP`, `ASPH`, `ASPH-G`, `Asphalt`, `PEM` and more. This map covers the values
 * that actually occur with meaningful frequency. The ETL reports every value it
 * could not classify, ordered by frequency, so the map can be extended from
 * evidence rather than guesswork.
 */
object SurfaceKinds {

    private val EXACT: Map<String, SurfaceKind> = buildMap {
        fun put(kind: SurfaceKind, vararg keys: String) = keys.forEach { put(it, kind) }

        put(
            SurfaceKind.HARD,
            "ASP", "ASPH", "ASPHALT", "ASPH-G", "ASPH-F", "ASPH-P", "ASPH-E", "ASPH-TURF",
            "CON", "CONC", "CONCRETE", "CON-G", "CONC-G", "COP", "COP-G", "PEM", "PER", "PAVED",
            "BIT", "BITUMEN", "TAR", "TARMAC", "MAC", "MACADAM", "ASPHALT-CONCRETE",
            // Engineered load-bearing surfaces: matting, planking and decks all behave
            // like a prepared surface for the purpose of "can this aircraft use it".
            "PSP", "MATS", "MATS-G", "MAT", "MET", "METAL", "STEEL", "BRICK", "WOOD",
            "ALUM", "ALUMINIUM", "ALUMINUM",
            "PIERCED STEEL PLANKING", "ROOF-TOP", "ROOF/TOP", "ROOFTOP",
            "PIERCED STEEL PLANKING / LANDING MATS / MEMBRANES",
            // Non-English spellings that occur in nationally sourced rows.
            "ASFALT", "ASFALTO", "HARD", "BETON", "BETÓN", "SEALED", "DECK",
        )
        put(
            SurfaceKind.GRASS,
            "GRS", "GRE", "GRASS", "TURF", "GRASS-G", "TURF-G", "SOD", "GVL-GRS", "GRASS/SOD",
            "GRAS", "GRASS-E", "GRASS-F", "GRASS-P",
        )
        put(
            SurfaceKind.GRAVEL,
            "GVL", "GRV", "GRVL", "GRAVEL", "GRAVEL-G", "CORAL", "COR", "LATERITE", "LAT",
            "CORAL-G", "CRUSHED ROCK", "PICARRA", "PIÇARRA", "STONE", "GRAV", "CALICHE",
        )
        put(
            SurfaceKind.DIRT,
            "DIRT", "GROUND", "EARTH", "CLAY", "CLA", "SOIL", "NATURAL", "UNPAVED",
            "GRADED-EARTH", "TER", "TERRA",
        )
        put(SurfaceKind.SAND, "SAN", "SAND", "SALT", "DESERT")
        put(SurfaceKind.WATER, "WAT", "WATER")
        put(SurfaceKind.SNOW_ICE, "SNO", "SNOW", "ICE")
        put(SurfaceKind.UNKNOWN, "UNK", "UNKNOWN", "")
    }

    /**
     * Classifies a raw surface string.
     *
     * Falls back to substring matching because the upstream data contains many
     * one-off compound spellings ("Asphalt over concrete", "GRASS / GRAVEL").
     */
    fun classify(raw: String): SurfaceKind {
        val key = raw.trim().uppercase()
        EXACT[key]?.let { return it }

        return when {
            key.containsAny("ASPH", "CONC", "PAVED", "BITUM", "TARMAC", "ASP", "CON") -> SurfaceKind.HARD
            key.containsAny("GRASS", "TURF", "GRS") -> SurfaceKind.GRASS
            key.containsAny("GRAVEL", "GVL", "GRVL", "CORAL") -> SurfaceKind.GRAVEL
            key.containsAny("DIRT", "EARTH", "GROUND", "CLAY", "SOIL") -> SurfaceKind.DIRT
            key.containsAny("SAND") -> SurfaceKind.SAND
            key.containsAny("WATER") -> SurfaceKind.WATER
            key.containsAny("SNOW", "ICE") -> SurfaceKind.SNOW_ICE
            else -> SurfaceKind.UNKNOWN
        }
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { this.contains(it) }
}
