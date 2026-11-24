package com.bourseyar.ai.pro

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Typed market-watch row; absent source fields remain null instead of being fabricated. */
data class MarketWatchRow(
    val symbol: String,
    val last: Double,
    val close: Double?,
    val changePercent: Double?,
    val volume: Double?,
    val tradedValue: Double?,
    val tradeCount: Double?,
    val instrumentId: String?
)

data class MarketWatchParseResult(
    val rows: List<MarketWatchRow>,
    val diagnostics: String
)

/** Parses the public TSETMC market-watch JSON independently from Android UI and networking. */
object TsetmcMarketWatchParser {
    private fun firstNumber(row: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            if (!row.has(key) || row.isNull(key)) continue
            val value = row.opt(key)
            val number = when (value) {
                is Number -> value.toDouble()
                is String -> value.replace(",", "").toDoubleOrNull()
                else -> null
            }
            if (number != null && number.isFinite()) return number
        }
        return null
    }

    private fun firstText(row: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val value = row.optString(key).trim()
            if (value.isNotBlank() && value != "null") return value
        }
        return null
    }

    private fun firstPositiveNumber(row: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            val number = firstNumber(row, key)
            if (number != null && number > 0.0) return number
        }
        return null
    }

    // Prefer a populated alias over an earlier zero field; preserve zero only if all aliases are zero.
    private fun firstActivityNumber(row: JSONObject, vararg keys: String): Double? {
        var firstZero: Double? = null
        for (key in keys) {
            val number = firstNumber(row, key) ?: continue
            if (number > 0.0) return number
            if (number == 0.0 && firstZero == null) firstZero = number
        }
        return firstZero
    }

    fun parse(rows: JSONArray): MarketWatchParseResult {
        var objectRows = 0
        var symbolRows = 0
        var commonStockRows = 0
        var priceRows = 0
        val diagnostics = StringBuilder()
        val parsed = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            objectRows++
            val symbol = firstText(row, "lVal18AFC", "lva", "symbol", "instrumentName") ?: return@mapNotNull null
            symbolRows++
            val instrumentType = firstNumber(row, "yVal", "yval")?.toInt()
            if (instrumentType != 300) return@mapNotNull null
            commonStockRows++
            if (commonStockRows <= 5) diagnostics.append("نمونه سهام عادی $commonStockRows: ").append(row.toString().take(1800)).append('\n')
            val last = firstPositiveNumber(row, "pDrCotVal", "pl", "lastPrice", "last", "pdv") ?: return@mapNotNull null
            priceRows++
            val closeCandidate = firstPositiveNumber(row, "pClosing", "pcl", "closingPrice", "close")
            val close = closeCandidate?.takeIf { abs(it - last) / last <= 0.10 }
            val previousClose = firstPositiveNumber(row, "priceYesterday", "py")
            val changeBase = close ?: last
            val change = previousClose?.let { 100.0 * (changeBase - it) / it }
            val volume = firstActivityNumber(row, "qTotTran5J", "tvol", "volume", "qtj")
            val value = firstActivityNumber(row, "qTotCap", "tval", "tradedValue", "qtc")
            val trades = firstActivityNumber(row, "zTotTran", "tno", "tradeCount", "ztt")
            val instrumentId = firstText(row, "insCode", "inscode", "instrumentId", "instrumentID")
            MarketWatchRow(symbol, last, close, change, volume, value, trades, instrumentId)
        }.distinctBy { item -> item.instrumentId?.let { "id:$it" } ?: "row:${item.hashCode()}" }
            .sortedByDescending { it.tradedValue ?: 0.0 }
        val duplicateCount = priceRows - parsed.size
        diagnostics.append("\nردیف‌های شیء JSON: $objectRows\n")
            .append("ردیف‌های دارای نماد: $symbolRows\n")
            .append("سهام عادی با yVal=300: $commonStockRows\n")
            .append("ردیف‌های دارای قیمت مثبت: $priceRows\n")
            .append("تکراری‌های حذف‌شده با شناسه یکسان: $duplicateCount\n")
            .append("ردیف‌های نهایی معتبر سهام عادی: ${parsed.size}\n")
        return MarketWatchParseResult(parsed, diagnostics.toString())
    }
}
