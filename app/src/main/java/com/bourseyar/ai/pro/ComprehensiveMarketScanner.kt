package com.bourseyar.ai.pro


/**
 * Free public market-data integrations for BourseYar.
 * TSETMC endpoints are unofficial/public and may throttle, block, or change.
 * Missing fundamentals are kept missing; never fabricate a financial ratio.
 */
data class CodalFilingReference(
    val title: String,
    val publishDate: String?,
    val url: String?,
    val category: String = classifyCodalFiling(title),
    val excelUrl: String? = null,
    val hasExcel: Boolean = excelUrl != null
)

/** Title-only filing classification. It labels the notice; it does not infer financial values. */
internal fun classifyCodalFiling(title: String): String {
    val normalized = title.trim().replace("ي", "ی").replace("ك", "ک")
    fun hasAny(vararg terms: String) = terms.any { normalized.contains(it, ignoreCase = true) }
    return when {
        hasAny("صورت‌های مالی", "صورتهای مالی", "صورت مالی", "اظهارنظر حسابرس", "گزارش حسابرس") -> "صورت‌های مالی"
        hasAny("گزارش تفسیری مدیریت", "تفسیر مدیریت") -> "گزارش تفسیری مدیریت"
        hasAny("افزایش سرمایه", "پیشنهاد هیئت‌مدیره در خصوص افزایش") -> "افزایش سرمایه"
        hasAny("مجمع عمومی", "آگهی دعوت به مجمع", "تصمیمات مجمع") -> "مجمع"
        hasAny("پرداخت سود", "زمان‌بندی پرداخت سود", "سود سهام") -> "پرداخت سود سهام"
        hasAny("شفاف‌سازی", "شفاف سازی", "افشای اطلاعات بااهمیت", "اطلاعات بااهمیت") -> "افشای اطلاعات بااهمیت"
        hasAny("فعالیت ماهانه", "گزارش تولید و فروش", "تولید و فروش") -> "گزارش فعالیت ماهانه"
        hasAny("قرارداد", "مناقصه", "مزایده") -> "قرارداد / مناقصه"
        else -> "سایر اطلاعیه‌ها"
    }
}

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
    val revenue: Double? = null,
    val netProfit: Double? = null,
    val totalAssets: Double? = null,
    val totalEquity: Double? = null,
    val totalLiabilities: Double? = null,
    val sourceNote: String = "کدال عمومی؛ شاخص‌های مالی فقط در صورت استخراج معتبر نمایش داده می‌شوند",
    val recentFilings: List<CodalFilingReference> = emptyList()
) {
    val hasFinancialMetrics: Boolean get() = listOf(eps, pe, revenueGrowthPercent, netProfitGrowthPercent, roePercent, debtToEquity, revenue, netProfit, totalAssets, totalEquity, totalLiabilities).any { it != null }
}

/** Accept only HTTPS links hosted by Codal; relative report paths are resolved to the official host. */
internal fun safeCodalUrl(rawUrl: String?): String? {
    val candidate = rawUrl?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
    return runCatching {
        val absolute = if (candidate.startsWith("/")) "https://www.codal.ir$candidate" else candidate
        val parsed = URL(absolute)
        absolute.takeIf {
            parsed.protocol.equals("https", ignoreCase = true) &&
                parsed.userInfo == null &&
                (parsed.port == -1 || parsed.port == 443) &&
                (parsed.host.equals("codal.ir", ignoreCase = true) ||
                    parsed.host.endsWith(".codal.ir", ignoreCase = true))
        }
    }.getOrNull()
}

/** Excel files are served from Codal's dedicated official host; reject redirects/host spoofing in supplied URLs. */
internal fun safeCodalExcelUrl(rawUrl: String?): String? {
    val candidate = rawUrl?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
    return runCatching {
        val parsed = URL(candidate)
        candidate.takeIf {
            parsed.protocol.equals("https", ignoreCase = true) &&
                parsed.userInfo == null &&
                (parsed.port == -1 || parsed.port == 443) &&
                parsed.host.equals("excel.codal.ir", ignoreCase = true)
        }
    }.getOrNull()
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

data class MarketScanDiagnostics(
    val requestedSymbols: Int,
    val analyzedSymbols: Int,
    val rejectedForInsufficientHistory: Int,
    val totalFailedSymbols: Int,
    val failedSymbols: List<String>
) {
    val failedCount: Int get() = totalFailedSymbols
}

/** Whole-market liquidity shortlist, then conservative multi-factor ranking. */
object ComprehensiveMarketScanner {
    @Volatile var lastDiagnostics: MarketScanDiagnostics = MarketScanDiagnostics(0, 0, 0, 0, emptyList())
        private set
    fun scan(limit: Int = 20, historyCount: Int = 10): List<ComprehensiveCandidate> {
        val leaders = TsetmcHistoryProvider.fetchMarketLeaders(limit.coerceIn(5, 100))
        val output = mutableListOf<ComprehensiveCandidate>()
        var insufficientHistory = 0
        val failures = mutableListOf<String>()
        val selectedLeaders = leaders.take(historyCount.coerceIn(3, 20))
        selectedLeaders.forEachIndexed { index, leader ->
            try {
                val history = TsetmcHistoryProvider.fetchDaily(leader.symbol)
                val technical = AnalysisEngine.scanCandles(leader.symbol, history.candles, 0.0)
                if (technical == null) {
                    insufficientHistory++
                    failures += "${leader.symbol}: تاریخچه معتبر یا حداقل داده لازم برای تحلیل موجود نیست"
                    return@forEachIndexed
                }
                // Shared validated volume logic: 20 prior sessions, at least 10 valid.
                val relativeVolume = VolumeAnalysis.analyze(history.candles)?.relativeVolume
                val flow = runCatching { TsetmcHistoryProvider.fetchFiveSessionFlowScore(leader.symbol) }.getOrNull()
                val snapshot = runCatching { TsetmcHistoryProvider.fetchLiveSnapshot(leader.symbol) }.getOrNull()
                val quote = snapshot?.quote
                val last = quote?.last ?: quote?.close ?: technical.report.last
                // Keep the multi-symbol scanner away from Codal's aggressively rate-limited search API.
                // Filing titles remain available through the explicit per-symbol Codal search action.
                val tseFund = runCatching { TsetmcFundamentalApi.fetch(leader.symbol) }.getOrNull()
                val fundamental = tseFund?.let {
                    FundamentalSnapshot(
                        symbol = leader.symbol,
                        filingsFound = 0,
                        recentFilingTitles = emptyList(),
                        eps = it.eps,
                        pe = it.pe,
                        sourceNote = "EPS و P/E از اطلاعات ابزار TSETMC؛ فهرست اطلاعیه‌ها با جستجوی جداگانه کدال"
                    )
                }
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
                when {
                    relativeVolume == null -> warnings += "حجم یا میانگین ۲۰ جلسه‌ای معتبر نیست؛ تأیید حجمی نداریم"
                    relativeVolume >= 2.0 -> warnings += "حجم امروز حداقل ۲ برابر میانگین جلسات قبل است؛ تأیید حجمی قوی (نیازمند تأیید قیمت و نقدشوندگی)"
                    relativeVolume >= 1.5 -> warnings += "حجم امروز حداقل ۱.۵ برابر میانگین جلسات قبل است؛ افزایش حجم قابل توجه"
                    relativeVolume < 1.0 -> warnings += "حجم امروز کمتر از میانگین جلسات قبل است؛ قدرت حرکت کاهش می‌یابد"
                }
                val firstLevel = snapshot?.orderBook?.firstOrNull()
                val isQueue = firstLevel?.let { (it.bidPrice ?: 0.0) > 0 && (it.askPrice ?: 0.0) <= 0 } ?: false
                val tooExtended = technical.report.rsi?.let { it >= 78.0 } ?: false
                // Unknown relative volume is not evidence of illiquidity; the warning above records missing confirmation.
                val lowLiquidity = leader.tradedValue <= 0 || (relativeVolume != null && relativeVolume < 0.4)
                val reason = when {
                    isQueue -> "صف خرید/عدم امکان ورود تأیید شده؛ از پیشنهاد خرید حذف شد"
                    tooExtended -> "RSI بیش از حد بالا؛ از ورود دیرهنگام جلوگیری شد"
                    lowLiquidity -> "نقدشوندگی/حجم ناکافی؛ از فهرست خرید حذف شد"
                    else -> null
                }
                output += ComprehensiveCandidate(leader.symbol, last, technicalScore, flow, relativeVolume, fundamental, score,
                    technical.report, stop, target1, target2, reason, warnings)
            } catch (e: Exception) {
                // Fail closed, but retain a short diagnostic so skipped symbols are visible to the user.
                val detail = e.message?.replace(Regex("\\s+"), " ")?.take(180) ?: e.javaClass.simpleName
                failures += "${leader.symbol}: $detail"
            }
            if (index < selectedLeaders.lastIndex) Thread.sleep(600)
        }
        lastDiagnostics = MarketScanDiagnostics(
            requestedSymbols = selectedLeaders.size,
            analyzedSymbols = output.size,
            rejectedForInsufficientHistory = insufficientHistory,
            totalFailedSymbols = failures.size,
            failedSymbols = failures.take(10)
        )
        return output.sortedWith(compareBy<ComprehensiveCandidate> { if (it.excludedReason == null) 0 else 1 }.thenByDescending { it.compositeScore })
    }
}
