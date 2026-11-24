package com.bourseyar.ai.pro

import java.net.HttpURLConnection
import java.net.URL

/** Raw HTTP snapshot from TSETMC, kept separate from Android UI and JSON parsing. */
data class MarketWatchHttpSnapshot(
    val statusCode: Int,
    val contentType: String,
    val body: String
)

object MarketWatchDataSource {
    fun fetch(endpoint: String): MarketWatchHttpSnapshot {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 BourseYarAIPro/0.3")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val contentType = connection.contentType ?: "نامشخص"
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return MarketWatchHttpSnapshot(code, contentType, body)
        } finally {
            connection.disconnect()
        }
    }
}
