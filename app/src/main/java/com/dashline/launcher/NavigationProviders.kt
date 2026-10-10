package com.dashline.launcher

import android.location.Location
import org.json.JSONObject
import org.mapsforge.core.model.LatLong
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class NavigationPlace(val name: String, val point: TravelCoordinate)

data class TravelCoordinate(val latitude: Double, val longitude: Double) {
    fun toLatLong() = LatLong(latitude, longitude)
}

data class NavigationRoute(
    val points: List<TravelCoordinate>,
    val distanceMeters: Double,
    val durationMillis: Long,
    val instructions: List<String>
)

interface NavigationProvider {
    fun search(query: String): List<NavigationPlace>
    fun route(origin: TravelCoordinate, destination: TravelCoordinate): NavigationRoute
}

class GraphHopperProxyProvider(baseUrl: String, private val clientToken: String? = null) : NavigationProvider {
    private val base = URL(baseUrl.trimEnd('/') + "/")

    init {
        require(base.protocol.equals("https", ignoreCase = true))
    }

    override fun search(query: String): List<NavigationPlace> {
        val url = URL(base, "api/navigation/geocode?q=${encode(query)}")
        val records = request(url).optJSONArray("records") ?: return emptyList()
        return (0 until records.length()).mapNotNull { index ->
            val record = records.optJSONObject(index) ?: return@mapNotNull null
            val latitude = record.optDouble("latitude", Double.NaN)
            val longitude = record.optDouble("longitude", Double.NaN)
            if (!validCoordinates(latitude, longitude)) return@mapNotNull null
            NavigationPlace(
                record.optString("name", query),
                TravelCoordinate(latitude, longitude)
            )
        }
    }

    override fun route(origin: TravelCoordinate, destination: TravelCoordinate): NavigationRoute {
        val url = URL(
            base,
            "api/navigation/route?origin=${origin.latitude},${origin.longitude}" +
                "&destination=${destination.latitude},${destination.longitude}&profile=foot"
        )
        val record = request(url).optJSONArray("records")?.optJSONObject(0)
            ?: throw IOException("The routing service returned no route")
        val coordinates = record.optJSONArray("geometry")
            ?: throw IOException("The routing service returned no route geometry")
        val points = (0 until coordinates.length()).mapNotNull { index ->
            val coordinate = coordinates.optJSONArray(index) ?: return@mapNotNull null
            if (coordinate.length() < 2) return@mapNotNull null
            val longitude = coordinate.optDouble(0, Double.NaN)
            val latitude = coordinate.optDouble(1, Double.NaN)
            if (validCoordinates(latitude, longitude)) TravelCoordinate(latitude, longitude) else null
        }
        if (points.size < 2) throw IOException("The routing service returned an invalid route")
        val instructionArray = record.optJSONArray("instructions")
        val instructions = if (instructionArray == null) emptyList() else {
            (0 until instructionArray.length()).mapNotNull { index ->
                instructionArray.optJSONObject(index)?.optString("text")
                    ?.takeIf { it.isNotBlank() }
            }
        }
        return NavigationRoute(
            points,
            record.optDouble("distance_meters", 0.0),
            record.optLong("duration_millis", 0L),
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
            clientToken?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Navigation service returned HTTP $status")
            val body = StringBuilder()
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                val buffer = CharArray(4096)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (body.length + count > MAX_RESPONSE_CHARS) throw IOException("Navigation response is too large")
                    body.append(buffer, 0, count)
                }
            }
            val response = JSONObject(body.toString())
            val message = response.optString("message")
            if (response.optString("freshness") == "NOT_AVAILABLE" && message.isNotBlank()) {
                throw IOException(message)
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val MAX_RESPONSE_CHARS = 1_000_000

        fun configured(value: String, token: String? = null): NavigationProvider? {
            if (value.isBlank()) return null
            return try {
                GraphHopperProxyProvider(value, token)
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

fun Location.toTravelCoordinate(): TravelCoordinate = TravelCoordinate(latitude, longitude)
