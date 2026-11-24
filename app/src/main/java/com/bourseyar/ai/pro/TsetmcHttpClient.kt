package com.bourseyar.ai.pro

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Small HTTP boundary for public TSETMC endpoints.
 * Only the official hosts used by this provider are allowed.
 */
internal object TsetmcHttpClient {
    private val allowedHosts = setOf("cdn.tsetmc.com", "members.tsetmc.com")

    internal fun validateUrl(value: String): URI {
        val uri = try {
            URI(value)
        } catch (_: Exception) {
            throw IllegalArgumentException("نشانی سرویس TSETMC نامعتبر است")
        }
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "نشانی سرویس TSETMC باید امن (HTTPS) باشد"
        }
        require(uri.host?.lowercase() in allowedHosts && uri.userInfo == null) {
            "اتصال فقط به میزبان‌های رسمی TSETMC مجاز است"
        }
        require(uri.port == -1 || uri.port == 443) {
            "درگاه اتصال به سرویس TSETMC نامعتبر است"
        }
        return uri
    }

    fun get(url: String): String {
        val validated = validateUrl(url)
        val connection = validated.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 BourseYarAIPro")
        connection.setRequestProperty("Accept", "application/json,text/plain,*/*")
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
