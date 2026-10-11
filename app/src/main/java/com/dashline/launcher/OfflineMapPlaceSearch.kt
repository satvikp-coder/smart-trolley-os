package com.dashline.launcher

import org.mapsforge.core.model.Tile
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.reader.MapFile
import java.io.File
import java.util.Locale

data class OfflineMapPlace(val name: String, val coordinate: TravelCoordinate)

object AhmedabadMapPlaceSearch {
    private const val ZOOM = 12.toByte()
    private const val TILE_SIZE = 256
    private const val SOUTH = 22.88
    private const val NORTH = 23.34
    private const val WEST = 72.40
    private const val EAST = 72.78
    private const val MAX_RESULTS = 24

    fun search(mapFile: File, query: String): List<OfflineMapPlace> {
        val term = query.trim().lowercase(Locale.ROOT)
        if (term.isEmpty() || !mapFile.isFile) return emptyList()
        val reader = MapFile(mapFile)
        try {
            val bounds = reader.boundingBox() ?: return emptyList()
            if (bounds.maxLatitude < SOUTH || bounds.minLatitude > NORTH ||
                bounds.maxLongitude < WEST || bounds.minLongitude > EAST
            ) return emptyList()

            val firstX = MercatorProjection.longitudeToTileX(WEST, ZOOM)
            val lastX = MercatorProjection.longitudeToTileX(EAST, ZOOM)
            val firstY = MercatorProjection.latitudeToTileY(NORTH, ZOOM)
            val lastY = MercatorProjection.latitudeToTileY(SOUTH, ZOOM)
            val matches = LinkedHashMap<String, OfflineMapPlace>()
            for (tileY in firstY..lastY) {
                for (tileX in firstX..lastX) {
                    val tile = Tile(tileX, tileY, ZOOM, TILE_SIZE)
                    reader.readPoiData(tile)?.pointOfInterests.orEmpty().forEach { poi ->
                        addMatch(matches, poi.tags, poi.position.latitude, poi.position.longitude, term)
                    }
                    reader.readMapData(tile)?.ways.orEmpty().forEach { way ->
                        val position = way.labelPosition ?: return@forEach
                        addMatch(matches, way.tags, position.latitude, position.longitude, term)
                    }
                }
            }
            return matches.values.sortedBy { it.name.lowercase(Locale.ROOT) }.take(MAX_RESULTS)
        } finally {
            reader.close()
        }
    }

    private fun addMatch(
        matches: MutableMap<String, OfflineMapPlace>,
        tags: List<org.mapsforge.core.model.Tag>,
        latitude: Double,
        longitude: Double,
        term: String
    ) {
        if (latitude !in SOUTH..NORTH || longitude !in WEST..EAST) return
        val name = tags.firstOrNull { it.key == "name" }?.value
            ?: tags.firstOrNull { it.key.startsWith("name:") }?.value
            ?: return
        if (!name.lowercase(Locale.ROOT).contains(term)) return
        val coordinate = TravelCoordinate(latitude, longitude)
        val key = "${name.lowercase(Locale.ROOT)}:${coordinate.latitude}:${coordinate.longitude}"
        matches[key] = OfflineMapPlace(name, coordinate)
    }
}

data class DemoTrain(val time: String, val name: String, val platform: Int)

object DemoRailwayBoard {
    val trains = listOf(
        DemoTrain("06:00", "DEMO Local Service A", 1),
        DemoTrain("08:15", "DEMO Intercity Service B", 2),
        DemoTrain("10:30", "DEMO Regional Service C", 3),
        DemoTrain("12:45", "DEMO Local Service D", 1),
        DemoTrain("15:00", "DEMO Intercity Service E", 4),
        DemoTrain("17:15", "DEMO Regional Service F", 2),
        DemoTrain("19:30", "DEMO Local Service G", 3),
        DemoTrain("21:45", "DEMO Evening Service H", 1)
    )
}
