package com.bourseyar.ai.pro

data class MarketRiskLevels(
    val stopLoss: Double,
    val target1: Double,
    val target2: Double
) {
    companion object {
        /**
         * Preserve the scanner's conservative stop rule: choose the lower of
         * technical support and the percentage-based stop when support exists.
         */
        fun calculate(lastPrice: Double, support: Double?, riskPercent: Double): MarketRiskLevels {
            val percentageStop = lastPrice * (1.0 - riskPercent / 100.0)
            val stop = support?.let { minOf(it, percentageStop) } ?: percentageStop
            val risk = lastPrice - stop
            return MarketRiskLevels(
                stopLoss = stop,
                target1 = lastPrice + risk * 1.5,
                target2 = lastPrice + risk * 2.0
            )
        }
    }
}
