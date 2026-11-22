package com.bourseyar.ai.pro

/**
 * Transparent 0..100 swing-opportunity score.
 * Missing optional inputs are excluded and remaining weights are renormalized.
 * This is a ranking aid, not a probability of profit or a trade instruction.
 */
object OpportunityScoring {
    fun score(
        technicalScore: Int,
        flowScore: Int?,
        relativeVolume: Double?,
        rewardRisk: Double?
    ): Int? {
        if (technicalScore !in 0..100) return null
        if (flowScore != null && flowScore !in 0..100) return null
        if (relativeVolume != null && (!relativeVolume.isFinite() || relativeVolume < 0.0)) return null
        if (rewardRisk != null && (!rewardRisk.isFinite() || rewardRisk < 0.0)) return null

        val parts = mutableListOf(technicalScore.toDouble() to 0.50)
        if (flowScore != null) parts += flowScore.toDouble() to 0.25
        if (relativeVolume != null) {
            val volumeScore = (relativeVolume / 2.0 * 100.0).coerceIn(0.0, 100.0)
            parts += volumeScore to 0.15
        }
        if (rewardRisk != null) {
            val rrScore = ((rewardRisk - 1.0) / 1.5 * 100.0).coerceIn(0.0, 100.0)
            parts += rrScore to 0.10
        }
        val totalWeight = parts.sumOf { it.second }
        if (totalWeight <= 0.0) return null
        return (parts.sumOf { it.first * it.second } / totalWeight)
            .toInt().coerceIn(0, 100)
    }
}
