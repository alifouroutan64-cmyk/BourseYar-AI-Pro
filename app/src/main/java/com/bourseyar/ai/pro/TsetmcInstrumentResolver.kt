package com.bourseyar.ai.pro

import org.json.JSONObject

/** Resolves an exact TSETMC instrument symbol from an InstrumentSearch response. */
internal object TsetmcInstrumentResolver {
    private fun normalize(value: String): String =
        value.trim().replace("ي", "ی").replace("ك", "ک")

    fun resolve(symbol: String, responseBody: String): String? {
        val requested = normalize(symbol)
        require(requested.isNotBlank()) { "نام نماد خالی است" }

        val root = JSONObject(responseBody)
        val items = root.optJSONArray("instrumentSearch")
            ?: error("ساختار پاسخ جستجوی نماد TSETMC تغییر کرده است")

        val match = (0 until items.length())
            .mapNotNull { items.optJSONObject(it) }
            .firstOrNull { normalize(it.optString("lVal18AFC")) == requested }
            ?: return null

        val code = match.optString("insCode")
        require(code.matches(Regex("\\d{5,25}"))) {
            "کد معاملاتی نماد نامعتبر است"
        }
        return code
    }
}
