package com.bourseyar.ai.pro

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CodalFilingSearchResult(
    val symbol: String,
    val filingsFound: Int,
    val recentFilingTitles: List<String>
)

/**
 * Public Codal search endpoint adapter.
 * Search results are metadata only; no financial values are inferred from titles.
 */
object CodalPublicApi {
    private const val SEARCH_ENDPOINT = "https://search.codal.ir/api/search/v2/q"

    fun searchFilings(symbol: String, limit: Int = 30): CodalFilingSearchResult {
        val normalized = symbol.trim()
            .replace("ي", "ی")
            .replace("ك", "ک")
        require(normalized.isNotBlank()) { "نماد برای جستجوی کدال خالی است" }
        require(limit in 1..100) { "تعداد اطلاعیه باید بین ۱ تا ۱۰۰ باشد" }

        val encoded = URLEncoder.encode(normalized, "UTF-8")
        val url = "$SEARCH_ENDPOINT?Symbol=$encoded&PageNumber=1&PageSize=$limit&LetterType=-1"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 20000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 BourseYarAIPro")
            setRequestProperty("Accept", "application/json,text/plain,*/*")
        }
        val raw: String
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            require(code in 200..299) { "کدال پاسخ HTTP $code برگرداند" }
            require(raw.isNotBlank()) { "پاسخ جستجوی کدال خالی است" }
        } finally {
            connection.disconnect()
        }

        val root = JSONObject(raw)
        val rows = root.optJSONArray("Letters")
            ?: root.optJSONArray("letters")
            ?: root.optJSONArray("Reports")
            ?: root.optJSONArray("reports")
            ?: error("ساختار پاسخ جستجوی کدال تغییر کرده است؛ آرایه اطلاعیه‌ها پیدا نشد")

        val titles = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val rowSymbol = sequenceOf("Symbol", "symbol", "Ticker", "ticker")
                .map { row.optString(it).trim() }
                .firstOrNull { it.isNotBlank() && it != "null" }
            // Search endpoint may return close matches. Keep exact normalized symbol only
            // when the response supplies a symbol field; otherwise retain the server-filtered row.
            if (rowSymbol != null &&
                rowSymbol.replace("ي", "ی").replace("ك", "ک") != normalized) return@mapNotNull null
            sequenceOf("Title", "title", "LetterTitle", "letterTitle", "Name", "name")
                .map { row.optString(it).trim() }
                .firstOrNull { it.isNotBlank() && it != "null" }
        }.distinct().take(limit)

        val total = sequenceOf("Total", "total", "TotalCount", "totalCount", "Count", "count")
            .mapNotNull { key -> root.opt(key)?.toString()?.toIntOrNull() }
            .firstOrNull { it >= 0 }
            ?: titles.size

        return CodalFilingSearchResult(normalized, maxOf(total, titles.size), titles)
    }
}
