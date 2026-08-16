package com.github.daanbouwman.flightplanner.airportdb

import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.AirportIndexBuilder
import java.sql.Connection

/**
 * Builds the in-memory index from the generated database.
 *
 * Shared by the builder, which serialises the result into the prebuilt index
 * asset, and the verifier, which uses it to generate real routes as a smoke
 * test. Both must derive the index exactly the way the app would.
 */
fun loadIndexFrom(conn: Connection): AirportIndex {
    val count = conn.createStatement().use { st ->
        st.executeQuery("SELECT COUNT(*) FROM airports").use { if (it.next()) it.getInt(1) else 0 }
    }
    val builder = AirportIndexBuilder(count)
    val sizeClasses = AirportSizeClass.entries.toTypedArray()

    conn.createStatement().use { st ->
        st.executeQuery(
            """
            SELECT id, code_packed, lat, lon, longest_runway_ft, has_icao, has_hard_surface,
                   has_lighting, size_class
            FROM airports
            """.trimIndent(),
        ).use { rs ->
            while (rs.next()) {
                builder.add(
                    id = rs.getInt(1),
                    packedCode = rs.getInt(2),
                    latitude = rs.getDouble(3),
                    longitude = rs.getDouble(4),
                    longestRunway = rs.getInt(5),
                    packedFlags = AirportIndex.packFlags(
                        hasIcao = rs.getInt(6) == 1,
                        hardSurface = rs.getInt(7) == 1,
                        lighting = rs.getInt(8) == 1,
                        sizeClass = sizeClasses[rs.getInt(9).coerceIn(0, sizeClasses.lastIndex)],
                    ),
                )
            }
        }
    }
    return builder.build()
}
