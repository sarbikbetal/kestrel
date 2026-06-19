package com.adsbdroid.ui.map

import com.adsbdroid.settings.MapType
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource

/** Maps a [MapType] preference to a concrete osmdroid tile source (no API keys needed). */
object MapTiles {

    private val terrain: ITileSource = XYTileSource(
        "OpenTopoMap",
        0, 17, 256, ".png",
        arrayOf(
            "https://a.tile.opentopomap.org/",
            "https://b.tile.opentopomap.org/",
            "https://c.tile.opentopomap.org/",
        ),
        "© OpenTopoMap (CC-BY-SA), © OpenStreetMap contributors",
    )

    private val humanitarian: ITileSource = XYTileSource(
        "HumanitarianOSM",
        0, 20, 256, ".png",
        arrayOf(
            "https://a.tile.openstreetmap.fr/hot/",
            "https://b.tile.openstreetmap.fr/hot/",
        ),
        "© OpenStreetMap contributors, Humanitarian OSM Team",
    )

    fun source(type: MapType): ITileSource = when (type) {
        MapType.STANDARD -> TileSourceFactory.MAPNIK
        MapType.TERRAIN -> terrain
        MapType.HUMANITARIAN -> humanitarian
    }
}
