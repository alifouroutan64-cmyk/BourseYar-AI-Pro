package com.bourseyar.ai.pro

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class HistoricalSeries(val symbol: String, val candles: List<Candle>)

/** Strict, dependency-free CSV reader: symbol,date,open,high,low,close,volume.
 * Dates must be ISO-8601 (yyyy-MM-dd); rows are sorted chronologically.
 * Quoted CSV fields are intentionally unsupported; TSETMC symbols contain no commas.
 */
object MarketCsvParser {
    private fun validIsoDate(value: String): Boolean {
        if (!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return false
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val pos = ParsePosition(0)
        return parser.parse(value, pos) != null && pos.index == value.length
    }

    fun parse(text: String): List<HistoricalSeries> {
        val rows = text.lineSequence().map { it.trim() }.filter { it.isNotBlank() }
            .filterNot { it.startsWith("#") }.toList()
        require(rows.isNotEmpty()) { "فایل CSV خالی است" }
        val start = if (rows.first().lowercase().startsWith("symbol,")) 1 else 0
        val grouped = linkedMapOf<String, MutableList<Pair<String, Candle>>>()
        rows.drop(start).forEachIndexed { index, row ->
            val p = row.split(",").map { it.trim() }
            require(p.size == 7) { "ردیف ${index + start + 1}: باید دقیقاً ۷ ستون داشته باشد" }
            val symbol = p[0].uppercase()
            require(symbol.isNotBlank()) { "نماد خالی در ردیف ${index + start + 1}" }
            val date = p[1]
            require(validIsoDate(date)) { "تاریخ ردیف ${index + start + 1} باید یک تاریخ معتبر yyyy-MM-dd باشد" }
            val o = p[2].toDoubleOrNull() ?: error("قیمت بازشدن نامعتبر در ردیف ${index + start + 1}")
            val h = p[3].toDoubleOrNull() ?: error("بیشینه نامعتبر در ردیف ${index + start + 1}")
            val l = p[4].toDoubleOrNull() ?: error("کمینه نامعتبر در ردیف ${index + start + 1}")
            val c = p[5].toDoubleOrNull() ?: error("قیمت پایانی نامعتبر در ردیف ${index + start + 1}")
            val v = p[6].toDoubleOrNull() ?: error("حجم نامعتبر در ردیف ${index + start + 1}")
            require(listOf(o,h,l,c,v).all { it.isFinite() } && l > 0 && o > 0 && c > 0 && h >= maxOf(o,l,c) && l <= minOf(o,c) && v >= 0) {
                "مقادیر OHLCV نامعتبر در ردیف ${index + start + 1}"
            }
            grouped.getOrPut(symbol) { mutableListOf() }.add(date to Candle(c,h,l,v))
        }
        require(grouped.isNotEmpty()) { "هیچ ردیف داده‌ای وجود ندارد" }
        return grouped.map { (symbol, entries) ->
            require(entries.map { it.first }.distinct().size == entries.size) { "تاریخ تکراری برای نماد $symbol" }
            val candles = entries.sortedBy { it.first }.map { it.second }
            require(candles.size >= 50) { "نماد $symbol فقط ${candles.size} کندل دارد؛ حداقل ۵۰ کندل لازم است" }
            HistoricalSeries(symbol, candles)
        }
    }
}
