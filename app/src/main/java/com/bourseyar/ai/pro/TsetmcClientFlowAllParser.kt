package com.bourseyar.ai.pro

import org.json.JSONObject

/** Pure parser for the latest all-market TSETMC client-flow snapshot. */
internal object TsetmcClientFlowAllParser {
    private fun JSONObject.numberOrNull(vararg keys: String): Double? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = opt(key)
            val parsed = when (value) {
                is Number -> value.toDouble()
                is String -> value.replace(",", "").toDoubleOrNull()
                else -> null
            }
            if (parsed != null && parsed.isFinite() && parsed >= 0.0) return parsed
        }
        return null
    }

    fun parse(body: String): Map<String, ClientFlow> {
        val root = JSONObject(body)
        val rows = root.optJSONArray("clientTypeAllDto")
            ?: error("ساختار جریان حقیقی/حقوقی TSETMC تغییر کرده است")
        val result = linkedMapOf<String, ClientFlow>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val code = row.optString("insCode")
            if (!code.matches(Regex("\\d{5,25}"))) continue
            val buyI = row.numberOrNull("buy_I_Volume", "buyIVolume")
            val sellI = row.numberOrNull("sell_I_Volume", "sellIVolume")
            val buyN = row.numberOrNull("buy_N_Volume", "buyNVolume")
            val sellN = row.numberOrNull("sell_N_Volume", "sellNVolume")
            val buyCountI = row.numberOrNull("buy_CountI", "buy_I_Count", "buyCountI")
            val sellCountI = row.numberOrNull("sell_CountI", "sell_I_Count", "sellCountI")
            if (buyI == null || sellI == null || buyN == null || sellN == null) continue
            result[code] = ClientFlow(
                buyI - sellI,
                buyN - sellN,
                buyCountI?.takeIf { it > 0.0 }?.let { buyI / it },
                sellCountI?.takeIf { it > 0.0 }?.let { sellI / it }
            )
        }
        require(result.isNotEmpty()) { "داده جریان حقیقی/حقوقی خالی است یا سرویس پاسخ معتبر نداد" }
        return result
    }
}
