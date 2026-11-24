package com.bourseyar.ai.pro

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class TsetmcFundamentalMetrics(val eps: Double?, val pe: Double?, val sector: String?)

/** Normalize Persian/Arabic symbol spelling before exact TSETMC instrument matching. */
internal fun normalizeTsetmcSymbol(value: String): String =
    value.trim()
        .replace("ي", "ی")
        .replace("ى", "ی")
        .replace("ك", "ک")
        .replace("\u200c", "")
        .replace(Regex("\\s+"), "")
        .lowercase(java.util.Locale.ROOT)

/** Select only an exact symbol match; search ranking must never silently choose a different instrument. */
internal fun selectExactTsetmcInstrumentCode(instruments: List<Pair<String, String>>, requestedSymbol: String): String? {
    val requested = normalizeTsetmcSymbol(requestedSymbol)
    if (requested.isBlank()) return null
    return instruments.firstOrNull { (symbol, _) ->
        normalizeTsetmcSymbol(symbol) == requested
    }?.second?.takeIf { it.isNotBlank() && it != "null" }
}

/** Best-effort public instrument fundamentals (EPS/P-E) from TSETMC; never marks missing fields as zero. */
object TsetmcFundamentalApi {
    private const val BASE = "https://cdn.tsetmc.com/api"

    internal fun parseInstrumentInfo(info: JSONObject): TsetmcFundamentalMetrics {
        val obj = info.optJSONObject("instrumentInfo") ?: info
        val eps = numeric(obj, "eps", "EPS", "estimatedEPS")
        val pe = numeric(obj, "pe", "P/E", "peRatio")
        val sector = obj.optString("sector", obj.optString("sectorName", null))
        return TsetmcFundamentalMetrics(eps, pe, sector.takeIf { !it.isNullOrBlank() })
    }

    private fun numeric(obj: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            val value = obj.opt(key)
            val parsed = when (value) {
                is Number -> value.toDouble()
                is String -> value.replace(",", "").toDoubleOrNull()
                else -> null
            }
            if (parsed != null && parsed.isFinite()) return parsed
        }
        return null
    }

    fun fetch(symbol: String): TsetmcFundamentalMetrics {
        val search = JSONObject(TsetmcHttpClient.get(
            "$BASE/Instrument/GetInstrumentSearch/${URLEncoder.encode(symbol, "UTF-8")}"
        ))
        val rows = search.optJSONArray("instrumentSearch") ?: JSONArray()
        val instruments = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val name = sequenceOf("lVal18AFC", "lval18afc", "symbol", "Symbol", "instrumentName", "InstrumentName")
                .map { row.optString(it).trim() }
                .firstOrNull { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
            val code = row.optString("insCode").trim()
            if (code.isBlank() || code == "null") null else name to code
        }
        val code = selectExactTsetmcInstrumentCode(instruments, symbol)
            ?: error("نماد دقیق $symbol در نتایج جستجوی TSETMC پیدا نشد؛ برای جلوگیری از نمایش اطلاعات نماد اشتباه، داده بنیادی رد شد")
        val info = JSONObject(TsetmcHttpClient.get(
            "$BASE/Instrument/GetInstrumentInfo/${URLEncoder.encode(code, "UTF-8")}"
        ))
        return parseInstrumentInfo(info)
    }
}
