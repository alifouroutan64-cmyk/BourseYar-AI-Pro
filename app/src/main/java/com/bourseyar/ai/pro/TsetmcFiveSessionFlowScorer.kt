package com.bourseyar.ai.pro

/**
 * Pure aggregation for the latest five available client-flow sessions.
 * Missing net/gross values are excluded rather than treated as zero.
 */
object TsetmcFiveSessionFlowScorer {
    fun score(history: List<ClientFlowDay>): Int? {
        val valid = history.takeLast(5).mapNotNull { day ->
            val net = day.netIndividualValue
            val gross = day.grossIndividualValue
            if (net == null || gross == null || !net.isFinite() || !gross.isFinite() || gross <= 0.0) {
                null
            } else {
                day to (net to gross)
            }
        }
        if (valid.isEmpty()) return null
        val netTotal = valid.sumOf { it.second.first }
        val grossTotal = valid.sumOf { it.second.second }
        val positiveDays = valid.count { it.second.first > 0.0 }
        return AnalysisEngine.normalizedFlowScore(netTotal, grossTotal, positiveDays, valid.size)
    }
}
