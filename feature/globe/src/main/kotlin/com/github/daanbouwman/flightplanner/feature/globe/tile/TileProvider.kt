package com.github.daanbouwman.flightplanner.feature.globe.tile

import com.github.daanbouwman.flightplanner.feature.globe.BuildConfig

/**
 * The credit a provider's licence requires to be shown with its imagery.
 *
 * Four fields rather than one string, because the licences ask for different
 * things in different places and a bare string carried one of them. [label] is
 * the short line drawn over the globe itself, always. [credit] is drawn under
 * it on the glass when present: Esri's terms want the data providers' names *on
 * the map*, and that is a list of four organisations that wraps onto a second
 * line; NASA's acknowledgement is a sentence, belongs on the Licences screen,
 * and is null here. [notice] is the full text Settings' About section and the
 * Licences screen carry, and [url] is the page the credit has to link to.
 *
 * Public, unlike the provider it belongs to, so the app can put it on a
 * licences screen without reaching into the tile pipeline.
 */
data class ImageryAttribution(
    /** Drawn over the globe, always — short enough to sit in a corner. */
    val label: String,
    /** Drawn under [label] on the glass when non-null. */
    val credit: String?,
    /** The full text for Settings' About section and the Licences screen. */
    val notice: String,
    /** Where the credit links. */
    val url: String,
)

/**
 * Where the globe's imagery comes from.
 *
 * Two implementations, and the renderer knows neither: everything above this
 * line deals in `(z, x, y)` and bytes. [TileProviders.select] picks between them
 * from whether an API key was built in, which is what lets a clone of this repo
 * with no key still build and still show a globe.
 */
internal interface TileProvider {

    /** The deepest level this source actually publishes. */
    val maxLevel: Int

    /**
     * The credit that must be drawn over the imagery.
     *
     * On the provider rather than in the UI because it is a property of the
     * source: swapping the provider and forgetting the credit is exactly the
     * mistake this placement makes impossible.
     */
    val attribution: ImageryAttribution

    /**
     * True when a tile fetched once is correct forever.
     *
     * This decides the cache policy in [TileHttp]: an immutable layer is stored
     * for a year regardless of what the origin's `Cache-Control` says, a mutable
     * one is stored for exactly as long as the origin allows.
     */
    val immutable: Boolean

    fun tileUrl(z: Int, x: Int, y: Int): String
}

/**
 * NASA's Global Imagery Browse Services, Blue Marble with shaded relief and
 * bathymetry — the keyless fallback.
 *
 * Kept for three properties rather than for how it looks. It needs **no API
 * key**, so a clone with nothing provisioned still shows a planet. It is
 * **public domain**, a US Government work, so no licensing question arises. And
 * it is a **static** layer with no date in the path, which is what makes the
 * disk cache and the pinned base levels worth anything — a tile fetched once is
 * correct forever, so airplane mode keeps whatever has been seen.
 *
 * Its ceiling is level 8. Zooming past that stops sharpening rather than
 * failing: the traversal simply stops subdividing, so the deepest tiles stretch.
 * That is the honest presentation of "this is as much detail as there is", and
 * it is why there is no error state for it. GIBS answers a request above the
 * ceiling with HTTP 400, which the loader records as permanently absent rather
 * than retrying.
 *
 * The attribution is the acknowledgement GIBS asks for on its developer portal.
 * It used to read "Imagery © NASA GIBS", which asserts a copyright that does not
 * exist over a US Government work. The acknowledgement is a full sentence, so it
 * goes to the Licences screen as the [ImageryAttribution.notice] and the glass
 * carries only the label.
 */
internal object NasaGibsBlueMarble : TileProvider {

    override val maxLevel: Int = 8

    override val immutable: Boolean = true

    override val attribution: ImageryAttribution = ImageryAttribution(
        label = "Imagery: NASA GIBS",
        credit = null,
        notice = "We acknowledge the use of imagery provided by services from NASA's Global " +
            "Imagery Browse Services (GIBS), part of NASA's Earth Science Data and Information " +
            "System (ESDIS).",
        url = "https://www.earthdata.nasa.gov/engage/open-data-services-software/" +
            "earthdata-developer-portal/gibs-api",
    )

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

/**
 * Esri World Imagery through the ArcGIS Location Platform, with an API key.
 *
 * This is the same pyramid the Rust reference draws — `providers.rs` hits the
 * keyless legacy host `server.arcgisonline.com` — served through the keyed
 * endpoint that the layer's licence actually permits an application to use. The
 * legacy item's terms forbid offline tile export, and a keyless client has no
 * standing under them at all; the Location Platform key is what puts this use
 * on the right side of that line.
 *
 * [maxLevel] is 18, ported from `quadtree.rs:19` (`MAX_LOD = 18`) rather than
 * the 19 the layer nominally publishes in places: the reference chose 18 and
 * nothing here is sharper than the reference.
 *
 * [immutable] is **false**. World Imagery is refreshed, so the origin's
 * `Cache-Control` is honoured as sent rather than overridden. Whether the
 * licence permits caching this layer for longer than the origin allows is an
 * open question this code does not answer; it is recorded here so that the
 * question is not mistaken for settled.
 *
 * The attribution is what Esri's terms ask for on the map itself — "Powered by
 * Esri" and the data providers — and, as the [ImageryAttribution.notice], the
 * form the terms give for a text credit.
 */
internal class EsriWorldImagery(private val token: String) : TileProvider {

    override val maxLevel: Int = 18

    override val immutable: Boolean = false

    override val attribution: ImageryAttribution = ImageryAttribution(
        label = "Powered by Esri",
        credit = "Esri, Vantor, Earthstar Geographics, and the GIS User Community",
        notice = "World Imagery basemap: Esri, Vantor, Earthstar Geographics, and the GIS User " +
            "Community. Powered by Esri.",
        url = "https://www.esri.com/en-us/legal/terms/data-attributions",
    )

    private companion object {
        const val ENDPOINT =
            "https://ibasemaps-api.arcgis.com/arcgis/rest/services/World_Imagery/MapServer/tile"
    }

    /**
     * Row before column — `{z}/{y}/{x}`, the same order as GIBS and as the
     * reference's `providers.rs:11`. The key travels as a query parameter, which
     * is the form the Location Platform documents for tile requests.
     */
    override fun tileUrl(z: Int, x: Int, y: Int): String = "$ENDPOINT/$z/$y/$x?token=$token"
}

/** Chooses the imagery source from what was built in. */
internal object TileProviders {

    /**
     * Esri when [apiKey] is non-blank, GIBS otherwise.
     *
     * A function of the key rather than a constant so a test can exercise both
     * choices; production reads [active].
     */
    fun select(apiKey: String?): TileProvider =
        if (apiKey.isNullOrBlank()) NasaGibsBlueMarble else EsriWorldImagery(apiKey.trim())

    /**
     * The provider this build uses, chosen once.
     *
     * The key is `BuildConfig.ARCGIS_API_KEY`, which the module's build script
     * reads from `local.properties` or the environment and which is empty in a
     * fresh clone. A clone without a key therefore still builds and still shows
     * a globe — a coarser one, with a different credit on it. Lazy so the choice
     * is made on first use, by whichever of the session and the credit plate is
     * reached first, and never at application start.
     */
    val active: TileProvider by lazy { select(BuildConfig.ARCGIS_API_KEY) }
}
