package com.example.pharmasync.ui.explore

import com.example.pharmasync.BuildConfig
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex

/**
 * Map tiles. CARTO's Voyager basemap is used when a key is configured (detailed labels, retina
 * tiles, no billing account needed); otherwise plain OpenStreetMap tiles are used.
 */
object BasemapTiles {

    val source: ITileSource by lazy {
        if (BuildConfig.CARTO_API_KEY.isBlank()) TileSourceFactory.MAPNIK else cartoVoyager(BuildConfig.CARTO_API_KEY)
    }

    val attribution: String
        get() = if (BuildConfig.CARTO_API_KEY.isBlank()) "© OpenStreetMap contributors" else "© CARTO © OpenStreetMap contributors"

    private fun cartoVoyager(key: String) = object : OnlineTileSourceBase(
        "CartoVoyager",
        0,
        20,
        // Retina tiles: 512 px images for crisp labels on phone screens.
        512,
        ".png",
        arrayOf("https://basemaps.cartocdn.com/rastertiles/voyager/"),
        "© CARTO, © OpenStreetMap contributors",
        TileSourcePolicy(
            4,
            TileSourcePolicy.FLAG_NO_BULK or
                TileSourcePolicy.FLAG_NO_PREVENTIVE or
                TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or
                TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED,
        ),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String = buildString {
            append(baseUrl)
            append(MapTileIndex.getZoom(pMapTileIndex))
            append('/')
            append(MapTileIndex.getX(pMapTileIndex))
            append('/')
            append(MapTileIndex.getY(pMapTileIndex))
            append("@2x")
            append(mImageFilenameEnding)
            append("?key=")
            append(key)
        }
    }
}
