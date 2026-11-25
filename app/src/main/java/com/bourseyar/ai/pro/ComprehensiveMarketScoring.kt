package com.bourseyar.ai.pro

/**
 * Pure, transparent scoring rules used by the whole-market scanner.
 * Missing optional inputs are excluded and the remaining weights are normalized.
 */
object ComprehensiveMarketScoring {
    fun fundamentalScore(snapshot: FundamentalSnapshot?): Int? {
        if (snapshot == null || !snapshot.hasFinancialMetrics) return null
        var score = 50
        snapshot.eps?.let { if (it > 0.0) score += 15 else score -= 15 }
        snapshot.pe?.let { if (it in 1.0..15.0) score += 15 else if (it > 30.0) score -= 10 }
        snapshot.revenueGrowthPercent?.let { if (it > 0.0) score += 15 else score -= 10 }
        snapshot.netProfitGrowthPercent?.let { if (it > 0.0) score += 15 else score -= 10 }
        snapshot.roePercent?.let { if (it >= 15.0) score += 10 else if (it < 0.0) score -= 10 }
        snapshot.debtToEquity?.let { if (it > 2.0) score -= 15 else if (it < 1.0) score += 10 }
        return score.coerceIn(0, 100)
    }

    fun compositeScore(
        technicalScore: Int,
        flowScore: Int?,
        relativeVolume: Double?,
        fundamentalScore: Int?
    ): Int {
        require(technicalScore in 0..100) { "امتیاز تکنیکال باید بین ۰ تا ۱۰۰ باشد" }
        require(flowScore == null || flowScore in 0..100) { "امتیاز جریان پول نامعتبر است" }
        require(fundamentalScore == null || fundamentalScore in 0..100) { "امتیاز بنیادی نامعتبر است" }
        require(relativeVolume == null || (relativeVolume.isFinite() && relativeVolume >= 0.0)) {
            "حجم نسبی نامعتبر است"
        }

        val components = mutableListOf(technicalScore.toDouble() to 0.45)
        if (flowScore != null) components += flowScore.toDouble() to 0.20
        if (relativeVolume != null) {
            components += ((relativeVolume / 2.0 * 100.0).coerceIn(0.0, 100.0)) to 0.15
        }
        if (fundamentalScore != null) components += fundamentalScore.toDouble() to 0.20
        val totalWeight = components.sumOf { it.second }
        return (components.sumOf { it.first * it.second } / totalWeight)
            .toInt().coerceIn(0, 100)
    }
}
