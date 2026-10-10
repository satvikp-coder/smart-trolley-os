package com.dashline.launcher

import android.location.Location
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class NavigationPlace(val name: String, val point: GeoPoint)

data class NavigationRoute(
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val durationMillis: Long,
    val instructions: List<String>
)

interface NavigationProvider {
    fun search(query: String): List<NavigationPlace>
    fun route(origin: GeoPoint, destination: GeoPoint): NavigationRoute
}

class GraphHopperProxyProvider(baseUrl: String) : NavigationProvider {
    private val base = URL(baseUrl.trimEnd('/') + "/")

    init {
        require(base.protocol.equals("https", ignoreCase = true))
    }

    override fun search(query: String): List<NavigationPlace> {
        val url = URL(base, "api/1/geocode?q=${encode(query)}&limit=5")
        val hits = request(url).optJSONArray("hits") ?: return emptyList()
        return (0 until hits.length()).mapNotNull { index ->
            val hit = hits.optJSONObject(index) ?: return@mapNotNull null
            val point = hit.optJSONObject("point") ?: return@mapNotNull null
            val latitude = point.optDouble("lat", Double.NaN)
            val longitude = point.optDouble("lng", Double.NaN)
            if (!validCoordinates(latitude, longitude)) return@mapNotNull null
            NavigationPlace(
                hit.optString("name", query),
                GeoPoint(latitude, longitude)
            )
        }
    }

    override fun route(origin: GeoPoint, destination: GeoPoint): NavigationRoute {
        val url = URL(
            base,
            "api/1/route?point=${origin.latitude},${origin.longitude}" +
                "&point=${destination.latitude},${destination.longitude}" +
                "&profile=foot&points_encoded=false&instructions=true"
        )
        val path = request(url).optJSONArray("paths")?.optJSONObject(0)
            ?: throw IOException("The routing service returned no route")
        val coordinates = path.optJSONObject("points")?.optJSONArray("coordinates")
            ?: throw IOException("The routing service returned no route geometry")
        val points = (0 until coordinates.length()).mapNotNull { index ->
            val coordinate = coordinates.optJSONArray(index) ?: return@mapNotNull null
            if (coordinate.length() < 2) return@mapNotNull null
            val longitude = coordinate.optDouble(0, Double.NaN)
            val latitude = coordinate.optDouble(1, Double.NaN)
            if (validCoordinates(latitude, longitude)) GeoPoint(latitude, longitude) else null
        }
        if (points.size < 2) throw IOException("The routing service returned an invalid route")
        val instructionArray = path.optJSONArray("instructions")
        val instructions = if (instructionArray == null) emptyList() else {
            (0 until instructionArray.length()).mapNotNull { index ->
                instructionArray.optJSONObject(index)?.optString("text")
                    ?.takeIf { it.isNotBlank() }
            }
        }
        return NavigationRoute(
            points,
            path.optDouble("distance", 0.0),
            path.optLong("time", 0L),
            instructions
        )
    }

    private fun request(url: URL): JSONObject {
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "SmartTrolleyOS/0.2 (Android; API 19+)")
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Navigation service returned HTTP $status")
            val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
            val body = StringBuilder()
            reader.useLines { lines ->
                lines.forEach { line ->
                    body.append(line)
                    if (body.length > MAX_RESPONSE_CHARS) throw IOException("Navigation response is too large")
                }
            }
            return JSONObject(body.toString())
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val MAX_RESPONSE_CHARS = 1_000_000

        fun configured(value: String): NavigationProvider? {
            if (value.isBlank()) return null
            return try {
                GraphHopperProxyProvider(value)
            } catch (_: Exception) {
                null
            }
        }

        fun validCoordinates(latitude: Double, longitude: Double): Boolean =
            latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0

        fun formatDistance(meters: Double): String = when {
            meters >= 1000 -> String.format(Locale.getDefault(), "%.1f km", meters / 1000.0)
            else -> String.format(Locale.getDefault(), "%.0f m", meters)
        }

        fun formatDuration(milliseconds: Long): String {
            val minutes = (milliseconds / 60_000L).coerceAtLeast(0L)
            val hours = minutes / 60
            val remainder = minutes % 60
            return if (hours > 0) "${hours}h ${remainder}m" else "${minutes} min"
        }
    }
}

fun Location.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
