package com.dashline.launcher

import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TravelApiClient(baseUrl: String, private val token: String?) {
    private val base = URL(baseUrl.trimEnd('/') + "/")

    init {
        require(base.protocol.equals("https", ignoreCase = true)) { "Travel API must use HTTPS" }
    }

    fun get(path: String, query: Map<String, String> = emptyMap()): JSONObject {
        require(!path.startsWith("/") && !path.contains(".."))
        val encodedQuery = query.entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }
        val url = URL(base, path + if (encodedQuery.isEmpty()) "" else "?$encodedQuery")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "SmartTrolleyOS/0.3 (Android API 19+)")
            token?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                ?: throw IOException("Travel service returned HTTP $status")
            val body = StringBuilder()
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                val buffer = CharArray(4096)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (body.length + count > MAX_RESPONSE_CHARS) throw IOException("Travel response is too large")
                    body.append(buffer, 0, count)
                }
            }
            if (status !in 200..299) throw IOException("Travel service returned HTTP $status")
            return JSONObject(body.toString())
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 7000
        private const val MAX_RESPONSE_CHARS = 1_000_000

        fun configured(baseUrl: String, token: String? = null): TravelApiClient? {
            if (baseUrl.isBlank()) return null
            return try {
                TravelApiClient(baseUrl, token)
            } catch (_: Exception) {
                null
            }
        }
    }
}
