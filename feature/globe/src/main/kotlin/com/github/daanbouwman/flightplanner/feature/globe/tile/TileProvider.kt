package com.github.daanbouwman.flightplanner.feature.globe.tile

/**
 * Where the globe's imagery comes from.
 *
 * An interface with one implementation, and that is deliberate: PLAN.md records
 * Esri's consumer tile licensing as unresolved, so the provider has to stay
 * swappable without the renderer knowing. Everything above this line deals in
 * `(z, x, y)` and bytes.
 */
internal interface TileProvider {

    /** The deepest level this source actually publishes. */
    val maxLevel: Int

    /**
     * The attribution string that must be drawn over the imagery.
     *
     * On the provider rather than in the UI because it is a property of the
     * source: swapping the provider and forgetting the credit is exactly the
     * mistake this placement makes impossible.
     */
    val attribution: String

    fun tileUrl(z: Int, x: Int, y: Int): String
}

/**
 * NASA's Global Imagery Browse Services, Blue Marble with shaded relief and
 * bathymetry.
 *
 * Chosen as the default for three properties rather than for how it looks.
 * It needs **no API key**, so nothing has to be provisioned or stored. It is
 * **public domain**, so the licensing question PLAN.md leaves open for Esri does
 * not arise. And it is a **static** layer with no date in the path, which is
 * what makes the disk cache and the pinned base levels worth anything — a tile
 * fetched once is correct forever, so airplane mode keeps whatever has been
 * seen.
 *
 * Its ceiling is level 8, which is where
 * [com.github.daanbouwman.flightplanner.feature.globe.math.Quadtree.MAX_LOD]
 * gets its value. Zooming past that stops sharpening rather than failing: the
 * traversal simply stops subdividing, so the deepest tiles stretch. That is the
 * honest presentation of "this is as much detail as there is", and it is why
 * there is no error state for it.
 */
internal object NasaGibsBlueMarble : TileProvider {

    override val maxLevel: Int = 8

    override val attribution: String = "Imagery © NASA GIBS"

    private const val ENDPOINT =
        "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/" +
            "BlueMarble_ShadedRelief_Bathymetry/default/GoogleMapsCompatible_Level8"

    /**
     * GIBS orders its REST path `{z}/{y}/{x}`, row before column — the reverse
     * of the `{z}/{x}/{y}` most slippy-map sources use. Getting this backwards
     * returns a real tile of the wrong place rather than a 404, so it is stated
     * here rather than left to be inferred from the string.
     */
    override fun tileUrl(z: Int, x: Int, y: Int): String = "$ENDPOINT/$z/$y/$x.jpeg"
}
