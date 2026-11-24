package com.bourseyar.ai.pro

import org.json.JSONObject

/** Pure parser for TSETMC individual/legal client-flow history responses. */
internal object TsetmcClientFlowHistoryParser {
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

    fun parse(body: String): List<ClientFlowDay> {
        val root = JSONObject(body)
        val rows = root.optJSONArray("clientType")
            ?: error("ساختار تاریخچه حقیقی/حقوقی TSETMC تغییر کرده است")
        val parsed = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val date = row.opt("recDate")?.toString().orEmpty()
            if (!date.matches(Regex("\\d{8}"))) return@mapNotNull null
            val buyI = row.numberOrNull("buy_I_Volume")
            val sellI = row.numberOrNull("sell_I_Volume")
            val buyN = row.numberOrNull("buy_N_Volume")
            val sellN = row.numberOrNull("sell_N_Volume")
            val buyIValue = row.numberOrNull("buy_I_Value")
            val sellIValue = row.numberOrNull("sell_I_Value")
            val buyCount = row.numberOrNull("buy_I_Count")
            val sellCount = row.numberOrNull("sell_I_Count")
            ClientFlowDay(
                date,
                if (buyI != null && sellI != null) buyI - sellI else null,
                if (buyIValue != null && sellIValue != null) buyIValue - sellIValue else null,
                if (buyIValue != null && sellIValue != null) buyIValue + sellIValue else null,
                if (buyN != null && sellN != null) buyN - sellN else null,
                if (buyI != null && buyCount != null && buyCount > 0.0) buyI / buyCount else null,
                if (sellI != null && sellCount != null && sellCount > 0.0) sellI / sellCount else null
            )
        }.distinctBy { it.date }.sortedBy { it.date }
        require(parsed.isNotEmpty()) { "تاریخچه جریان حقیقی/حقوقی برای این نماد خالی یا نامعتبر است" }
        return parsed
    }
}
