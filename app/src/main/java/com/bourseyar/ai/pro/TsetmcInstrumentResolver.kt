package com.bourseyar.ai.pro

import org.json.JSONObject

/** Resolves an exact TSETMC instrument symbol from an InstrumentSearch response. */
internal object TsetmcInstrumentResolver {
    private val instrumentCodePattern = Regex("\\d{5,25}")

    private fun normalize(value: String): String =
        value.trim().replace("ي", "ی").replace("ك", "ک")

    fun resolve(symbol: String, responseBody: String): String? {
        val requested = normalize(symbol)
        require(requested.isNotBlank()) { "نام نماد خالی است" }

        val root = JSONObject(responseBody)
        val items = root.optJSONArray("instrumentSearch")
            ?: error("ساختار پاسخ جستجوی نماد TSETMC تغییر کرده است")

        val exactMatches = (0 until items.length())
            .mapNotNull { items.optJSONObject(it) }
            .filter { normalize(it.optString("lVal18AFC")) == requested }

        val validMatch = exactMatches.firstOrNull {
            instrumentCodePattern.matches(it.optString("insCode"))
        }
        if (validMatch != null) return validMatch.optString("insCode")

        if (exactMatches.isNotEmpty()) {
            throw IllegalArgumentException("کد معاملاتی نماد نامعتبر است")
        }
        return null
    }
}
