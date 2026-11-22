package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisEngineTest {
    private fun flatCandles(size: Int = 90) = List(size) {
        Candle(open = 100.0, high = 101.0, low = 99.0, close = 100.0, volume = 1000.0)
    }

    @Test
    fun backtestReturnsAllHoldingHorizons() {
        val results = AnalysisEngine.backtest(flatCandles(), minScore = 0)
        assertEquals(listOf(2, 3, 4, 5, 6, 7), results.map { it.holdingDays })
        assertTrue(results.all { it.trades > 0 })
    }

    @Test
    fun roundTripFeeReducesAverageReturn() {
        val noFee = AnalysisEngine.backtest(flatCandles(), minScore = 0, feeBpsRoundTrip = 0.0)
        val withFee = AnalysisEngine.backtest(flatCandles(), minScore = 0, feeBpsRoundTrip = 100.0)
        assertTrue(withFee.zip(noFee).all { (fee, free) -> fee.averageReturnPercent < free.averageReturnPercent })
    }

    @Test
    fun invalidOhlcIsRejected() {
        val candles = flatCandles().toMutableList()
        candles[60] = Candle(open = 100.0, high = 98.0, low = 99.0, close = 100.0, volume = 1000.0)
        assertTrue(AnalysisEngine.backtest(candles, minScore = 0).isEmpty())
    }

    @Test
    fun normalizedFlowScoreRewardsPositivePressureAndConsistency() {
        val strong = AnalysisEngine.normalizedFlowScore(100.0, 100.0, 5, 5)
        val weak = AnalysisEngine.normalizedFlowScore(-100.0, 100.0, 0, 5)
        assertTrue(strong != null && weak != null && strong > weak)
        assertEquals(100, strong)
        assertEquals(0, weak)
    }

    @Test
    fun normalizedFlowScoreRejectsInvalidOrIncompleteTotals() {
        assertEquals(null, AnalysisEngine.normalizedFlowScore(10.0, 0.0, 1, 2))
        assertEquals(null, AnalysisEngine.normalizedFlowScore(110.0, 100.0, 1, 2))
        assertEquals(null, AnalysisEngine.normalizedFlowScore(Double.NaN, 100.0, 1, 2))
    }

    @Test
    fun insufficientHistoryIsRejected() {
        assertTrue(AnalysisEngine.backtest(flatCandles(59), minScore = 0).isEmpty())
    }

    @Test
    fun opportunityScoreCombinesTechnicalFlowVolumeAndRewardRisk() {
        val full = OpportunityScoring.score(80, 100, 2.0, 2.5)
        assertEquals(90, full)
    }

    @Test
    fun opportunityScoreRenormalizesWhenFlowIsUnavailable() {
        val withMissingFlow = OpportunityScoring.score(80, null, 2.0, 2.5)
        assertEquals(86, withMissingFlow)
    }

    @Test
    fun opportunityScoreRejectsInvalidInputs() {
        assertEquals(null, OpportunityScoring.score(101, 50, 1.0, 2.0))
        assertEquals(null, OpportunityScoring.score(70, 50, Double.NaN, 2.0))
        assertEquals(null, OpportunityScoring.score(70, 50, 1.0, -1.0))
    }
}
