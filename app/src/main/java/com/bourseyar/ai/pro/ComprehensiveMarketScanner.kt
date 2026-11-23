package com.bourseyar.ai.pro

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Free public market-data integrations for BourseYar.
 * TSETMC endpoints are unofficial/public and may throttle, block, or change.
 * Missing fundamentals are kept missing; never fabricate a financial ratio.
 */
data class FundamentalSnapshot(
    val symbol: String,
    val filingsFound: Int,
    val recentFilingTitles: List<String>,
    val eps: Double? = null,
    val pe: Double? = null,
    val revenueGrowthPercent: Double? = null,
    val netProfitGrowthPercent: Double? = null,
    val roePercent: Double? = null,
    val debtToEquity: Double? = null,
    val sourceNote: String = "کدال عمومی؛ شاخص‌های مالی فقط در صورت استخراج معتبر نمایش داده می‌شوند"
) {
    val hasFinancialMetrics: Boolean get() = listOf(eps, pe, revenueGrowthPercent, netProfitGrowthPercent, roePercent, debtToEquity).any { it != null }
}

/** Free Codal search API. Its schema is not a stable contract; this parser is intentionally defensive. */
object CodalPublicApi {
    private const val BASE = "https://search.codal.ir/api/search/v2/q"

    private fun request(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) BourseYarAIPro")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            require(code in 200..299) { "کدال HTTP $code" }
            require(body.isNotBlank()) { "پاسخ خالی کدال" }
            return body
        } finally { conn.disconnect() }
    }

    fun searchFilings(symbol: String, limit: Int = 20): FundamentalSnapshot {
        val encoded = URLEncoder.encode(symbol.trim(), "UTF-8")
        val urls = listOf(
            "${BASE}?search=${encoded}&PageNumber=1&PageSize=${limit}",
            "${BASE}?Symbol=${encoded}&PageNumber=1&PageSize=${limit}"
        )
        var lastError: Exception? = null
        for (url in urls) {
            try {
                val root = JSONObject(request(url))
                val rows = root.optJSONArray("Letters") ?: root.optJSONArray("letter") ?: root.optJSONArray("items")
                    ?: root.optJSONArray("data") ?: JSONArray()
                val titles = mutableListOf<String>()
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val title = row.optString("Title", row.optString("title", row.optString("LetterTitle", ""))).trim()
                    if (title.isNotBlank()) titles += title
                }
                if (rows.length() > 0 || root.has("Letters") || root.has("items") || root.has("data")) {
                    return FundamentalSnapshot(symbol, rows.length(), titles.take(5))
                }
            } catch (e: Exception) { lastError = e }
        }
        throw lastError ?: IllegalStateException("ساختار جستجوی کدال شناخته نشد")
    }
}

data class ComprehensiveCandidate(
    val symbol: String,
    val lastPrice: Double,
    val technicalScore: Int,
    val flowScore: Int?,
    val relativeVolume: Double?,
    val fundamental: FundamentalSnapshot?,
    val compositeScore: Int,
    val report: TechnicalReport,
    val stopLoss: Double,
    val target1: Double,
    val target2: Double,
    val excludedReason: String? = null,
    val dataWarnings: List<String> = emptyList()
) {
    val recommendation: String get() = when {
        compositeScore >= 80 -> "نامزد خرید مشروط"
        compositeScore >= 68 -> "زیر نظر / ورود پله‌ای فقط با تأیید"
        compositeScore >= 55 -> "نگهداری یا انتظار"
        else -> "عدم ورود"
    }
}

/** Whole-market liquidity shortlist, then conservative multi-factor ranking. */
object ComprehensiveMarketScanner {
    fun scan(limit: Int = 20, historyCount: Int = 10): List<ComprehensiveCandidate> {
        val leaders = TsetmcHistoryProvider.fetchMarketLeaders(limit.coerceIn(5, 100))
        val output = mutableListOf<ComprehensiveCandidate>()
        leaders.take(historyCount.coerceIn(3, 20)).forEachIndexed { index, leader ->
            try {
                val history = TsetmcHistoryProvider.fetchDaily(leader.symbol)
                val technical = AnalysisEngine.scanCandles(leader.symbol, history.candles, 0.0) ?: return@forEachIndexed
                val volumes = history.candles.dropLast(1).takeLast(20).map { it.volume }
                val averageVolume = volumes.average()
                val relativeVolume = if (averageVolume > 0) history.candles.last().volume / averageVolume else null
                val flow = runCatching { TsetmcHistoryProvider.fetchFiveSessionFlowScore(leader.symbol) }.getOrNull()
                val snapshot = runCatching { TsetmcHistoryProvider.fetchLiveSnapshot(leader.symbol) }.getOrNull()
                val quote = snapshot?.quote
                val last = quote?.last ?: quote?.close ?: technical.report.last
                val codal = runCatching { CodalPublicApi.searchFilings(leader.symbol) }.getOrNull()
                val tseFund = runCatching { TsetmcFundamentalApi.fetch(leader.symbol) }.getOrNull()
                val fundamental = if (codal != null || tseFund != null) FundamentalSnapshot(leader.symbol, codal?.filingsFound ?: 0, codal?.recentFilingTitles.orEmpty(), eps = tseFund?.eps, pe = tseFund?.pe) else null
                val stop = technical.report.support?.let { minOf(it, last * (1.0 - technical.riskPercent / 100.0)) }
                    ?: last * (1.0 - technical.riskPercent / 100.0)
                val risk = last - stop
                val target1 = last + risk * 1.5
                val target2 = last + risk * 2.0
                val rr = if (risk > 0) (target1 - last) / risk else null
                val technicalScore = technical.report.score
                // Codal metrics remain null unless actual financial values are parsed.
                val fundamentalScore: Int? = fundamental?.let { f ->
                    if (!f.hasFinancialMetrics) null else {
                        var score = 50
                        f.eps?.let { if (it > 0) score += 15 else score -= 15 }
                        f.pe?.let { if (it in 1.0..15.0) score += 15 else if (it > 30.0) score -= 10 }
                        f.revenueGrowthPercent?.let { if (it > 0) score += 15 else score -= 10 }
                        f.netProfitGrowthPercent?.let { if (it > 0) score += 15 else score -= 10 }
                        f.roePercent?.let { if (it >= 15) score += 10 else if (it < 0) score -= 10 }
                        f.debtToEquity?.let { if (it > 2) score -= 15 else if (it < 1) score += 10 }
                        score.coerceIn(0, 100)
                    }
                }
                val components = mutableListOf<Pair<Int, Double>>(technicalScore to 0.45)
                if (flow != null) components += (flow to 0.20)
                if (relativeVolume != null) components += (((relativeVolume / 2.0 * 100).coerceIn(0.0, 100.0).toInt()) to 0.15)
                if (fundamentalScore != null) components += (fundamentalScore to 0.20)
                val score = (components.sumOf { it.first * it.second } / components.sumOf { it.second }).toInt().coerceIn(0, 100)
                val warnings = mutableListOf<String>()
                if (fundamental?.hasFinancialMetrics != true) warnings += "نسب بنیادی قابل استخراج نبود؛ وزن بنیادی حذف شد"
                if (flow == null) warnings += "جریان پول حقیقی ناقص"
                if (quote == null) warnings += "تابلوی لحظه‌ای دریافت نشد"
                val firstLevel = snapshot?.orderBook?.firstOrNull()
                val isQueue = firstLevel?.let { (it.bidPrice ?: 0.0) > 0 && (it.askPrice ?: 0.0) <= 0 } ?: false
                val tooExtended = technical.report.rsi?.let { it >= 78.0 } ?: false
                val lowLiquidity = leader.tradedValue <= 0 || (relativeVolume != null && relativeVolume < 0.4)
                val reason = when {
                    isQueue -> "صف خرید/عدم امکان ورود تأیید شده؛ از پیشنهاد خرید حذف شد"
                    tooExtended -> "RSI بیش از حد بالا؛ از ورود دیرهنگام جلوگیری شد"
                    lowLiquidity -> "نقدشوندگی/حجم ناکافی؛ از فهرست خرید حذف شد"
                    else -> null
                }
                output += ComprehensiveCandidate(leader.symbol, last, technicalScore, flow, relativeVolume, fundamental, score,
                    technical.report, stop, target1, target2, reason, warnings)
            } catch (_: Exception) { /* fail closed: invalid/missing symbol data is not ranked */ }
            if (index < historyCount - 1) Thread.sleep(600)
        }
        return output.sortedWith(compareBy<ComprehensiveCandidate> { if (it.excludedReason == null) 0 else 1 }.thenByDescending { it.compositeScore })
    }
}


data class TsetmcFundamentalMetrics(val eps: Double?, val pe: Double?, val sector: String?)

/** Best-effort public instrument fundamentals (EPS/P-E) from TSETMC; never marks missing fields as zero. */
object TsetmcFundamentalApi {
    private const val BASE = "https://cdn.tsetmc.com/api"
    private fun get(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 15000
            setRequestProperty("User-Agent", "Mozilla/5.0 BourseYarAIPro")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            require(code in 200..299 && text.isNotBlank()) { "TSETMC اطلاعات بنیادی HTTP $code" }
            return JSONObject(text)
        } finally { connection.disconnect() }
    }
    private fun numeric(obj: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            val v = obj.opt(key)
            val n = when (v) { is Number -> v.toDouble(); is String -> v.replace(",", "").toDoubleOrNull(); else -> null }
            if (n != null && n.isFinite()) return n
        }
        return null
    }
    fun fetch(symbol: String): TsetmcFundamentalMetrics {
        val search = get("$BASE/Instrument/GetInstrumentSearch/${URLEncoder.encode(symbol, "UTF-8")}")
        val rows = search.optJSONArray("instrumentSearch") ?: JSONArray()
        val code = rows.optJSONObject(0)?.optString("insCode")?.takeIf { it.isNotBlank() }
            ?: error("کد ابزار نماد $symbol پیدا نشد")
        val info = get("$BASE/Instrument/GetInstrumentInfo/${URLEncoder.encode(code, "UTF-8")}")
        val obj = info.optJSONObject("instrumentInfo") ?: info
        val eps = numeric(obj, "eps", "EPS", "estimatedEPS")
        val pe = numeric(obj, "pe", "P/E", "peRatio")
        val sector = obj.optString("sector", obj.optString("sectorName", null))
        return TsetmcFundamentalMetrics(eps, pe, sector.takeIf { !it.isNullOrBlank() })
    }
}
