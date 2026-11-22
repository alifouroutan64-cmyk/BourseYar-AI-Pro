package com.bourseyar.ai.pro

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Public, unofficial TSETMC endpoints. Validate every response; provider availability is not guaranteed. */
object TsetmcHistoryProvider {
    private const val BASE = "https://cdn.tsetmc.com/api"
    private const val CHART = "https://members.tsetmc.com/tsev2/chart/data/Financial.aspx"

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 20000
            requestMethod = "GET"
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
        } finally { connection.disconnect() }
    }

    fun fetchDaily(symbol: String): HistoricalSeries {
        val query = URLEncoder.encode(symbol.trim(), "UTF-8")
        val search = JSONObject(get("$BASE/Instrument/GetInstrumentSearch/$query"))
        val items = search.optJSONArray("instrumentSearch")
            ?: error("ساختار پاسخ جستجوی نماد تغییر کرده است")
        val normalized = symbol.trim().replace("ي", "ی").replace("ك", "ک")
        val match = (0 until items.length()).map { items.getJSONObject(it) }
            .firstOrNull {
                it.optString("lVal18AFC").replace("ي", "ی").replace("ك", "ک") == normalized
            } ?: error("نماد دقیق «$symbol» در جستجوی TSETMC پیدا نشد")
        val code = match.optString("insCode")
        require(code.isNotBlank()) { "کد معاملاتی نماد دریافت نشد" }

        // a=1 requests backward-adjusted chart prices. Chart response columns:
        // date,pmax,pmin,pf,pl,tvol,pc
        val raw = get("$CHART?i=$code&t=ph&a=1")
        val candles = raw.split(";").mapNotNull { row ->
            val p = row.trim().split(",")
            if (p.size < 7) return@mapNotNull null
            val dateText = p[0].trim()
            val date = runCatching {
                LocalDate.parse(dateText, DateTimeFormatter.BASIC_ISO_DATE)
            }.getOrNull() ?: return@mapNotNull null
            val high = p[1].toDoubleOrNull() ?: return@mapNotNull null
            val low = p[2].toDoubleOrNull() ?: return@mapNotNull null
            val open = p[3].toDoubleOrNull() ?: return@mapNotNull null
            val last = p[4].toDoubleOrNull() ?: return@mapNotNull null
            val volume = p[5].toDoubleOrNull() ?: return@mapNotNull null
            val close = p[6].toDoubleOrNull() ?: return@mapNotNull null
            if (listOf(high, low, open, last, volume, close).any { !it.isFinite() } ||
                low <= 0 || open <= 0 || close <= 0 || high < maxOf(open, low, close) ||
                low > minOf(open, close) || volume < 0) return@mapNotNull null
            date to Candle(close = close, high = high, low = low, volume = volume)
        }.sortedBy { it.first }
        require(candles.size >= 50) {
            "TSETMC برای $symbol فقط ${candles.size} کندل معتبر داد؛ حداقل ۵۰ لازم است"
        }
        require(candles.map { it.first }.distinct().size == candles.size) {
            "تاریخ تکراری در تاریخچه $symbol"
        }
        return HistoricalSeries(symbol.trim().uppercase(), candles.map { it.second })
    }
}
