package com.bourseyar.ai.pro

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parses the semicolon-delimited daily chart response returned by TSETMC.
 * Expected columns: date,pmax,pmin,pf,pl,tvol,pc.
 */
internal object TsetmcDailyHistoryParser {
    private fun isValidDate(value: String): Boolean {
        if (!value.matches(Regex("\\d{8}"))) return false
        val parser = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val position = ParsePosition(0)
        return parser.parse(value, position) != null && position.index == value.length
    }

    fun parse(symbol: String, raw: String, minimumCandles: Int = 50): HistoricalSeries {
        require(symbol.isNotBlank()) { "نام نماد خالی است" }
        require(minimumCandles >= 1) { "حداقل تعداد کندل نامعتبر است" }
        val candles = raw.split(";").mapNotNull { row ->
            val columns = row.trim().split(",")
            if (columns.size < 7) return@mapNotNull null
            val date = columns[0].trim()
            if (!isValidDate(date)) return@mapNotNull null
            val high = columns[1].toDoubleOrNull() ?: return@mapNotNull null
            val low = columns[2].toDoubleOrNull() ?: return@mapNotNull null
            val open = columns[3].toDoubleOrNull() ?: return@mapNotNull null
            val last = columns[4].toDoubleOrNull() ?: return@mapNotNull null
            val volume = columns[5].toDoubleOrNull() ?: return@mapNotNull null
            val close = columns[6].toDoubleOrNull() ?: return@mapNotNull null
            if (listOf(high, low, open, last, volume, close).any { !it.isFinite() } ||
                low <= 0.0 || open <= 0.0 || close <= 0.0 ||
                high < maxOf(open, low, close) || low > minOf(open, close) || volume < 0.0
            ) return@mapNotNull null
            date to Candle(close = close, high = high, low = low, volume = volume, open = open)
        }.sortedBy { it.first }

        require(candles.size >= minimumCandles) {
            "TSETMC برای $symbol فقط ${candles.size} کندل معتبر داد؛ حداقل $minimumCandles لازم است"
        }
        require(candles.map { it.first }.distinct().size == candles.size) {
            "تاریخ تکراری در تاریخچه $symbol"
        }
        return HistoricalSeries(symbol.trim().uppercase(), candles.map { it.second })
    }
}
