package com.bourseyar.ai.pro

import org.json.JSONObject

/** Pure parser for TSETMC market-watch leaders, preserving the common-share filter. */
internal object TsetmcMarketLeadersParser {
    private fun JSONObject.numberOrNull(vararg keys: String): Double? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = opt(key)
            val parsed = when (value) {
                is Number -> value.toDouble()
                is String -> value.replace(",", "").toDoubleOrNull()
                else -> null
            }
            if (parsed != null && parsed.isFinite()) return parsed
        }
        return null
    }

    private fun JSONObject.positiveNumber(vararg keys: String): Double? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = opt(key)
            val parsed = when (value) {
                is Number -> value.toDouble()
                is String -> value.replace(",", "").toDoubleOrNull()
                else -> null
            }
            if (parsed != null && parsed.isFinite() && parsed > 0.0) return parsed
        }
        return null
    }

    fun parse(body: String, limit: Int = 20): List<MarketLeader> {
        require(limit in 1..100) { "تعداد نماد باید بین ۱ تا ۱۰۰ باشد" }
        val root = JSONObject(body)
        val rows = root.optJSONArray("marketwatch")
            ?: error("ساختار دیده‌بان بازار TSETMC تغییر کرده یا پاسخ مسدود شده است")
        val leaders = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val code = row.optString("insCode")
            val symbol = sequenceOf("lva", "lVal18AFC", "symbol")
                .map { row.optString(it) }
                .firstOrNull { it.isNotBlank() && it != "null" }.orEmpty()
            val instrumentType = row.numberOrNull("yVal", "yval")?.toInt()
            if (instrumentType != 300) return@mapNotNull null
            val volume = row.positiveNumber("qTotTran5J", "tvol", "volume", "qtj")
            val value = row.positiveNumber("qTotCap", "tval", "tradedValue", "qtc")
            if (code.isBlank() || symbol.isBlank() || volume == null || value == null) null
            else MarketLeader(symbol, code, volume, value)
        }
        require(leaders.isNotEmpty()) {
            "دیده‌بان بازار ردیف معتبر برنگرداند؛ ممکن است سرویس محدود یا ساختار پاسخ تغییر کرده باشد"
        }
        return leaders.sortedByDescending { it.tradedValue }.take(limit)
    }
}
