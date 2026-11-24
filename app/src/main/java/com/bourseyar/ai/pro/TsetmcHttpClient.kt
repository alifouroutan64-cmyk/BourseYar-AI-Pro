package com.bourseyar.ai.pro

import java.net.HttpURLConnection
import java.net.URL

/**
 * Small HTTP boundary for public TSETMC endpoints.
 * It validates status and body before handing data to the JSON/chart parsers.
 */
internal object TsetmcHttpClient {
    fun get(url: String): String {
        require(url.startsWith("https://")) { "نشانی سرویس TSETMC باید امن (HTTPS) باشد" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 BourseYarAIPro")
            setRequestProperty("Accept", "application/json,text/plain,*/*")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            require(code in 200..299) { "TSETMC پاسخ HTTP $code برگرداند" }
            require(body.isNotBlank()) { "پاسخ خالی از TSETMC" }
            return body
        } finally {
            connection.disconnect()
        }
    }
}
