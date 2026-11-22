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
    fun insufficientHistoryIsRejected() {
        assertTrue(AnalysisEngine.backtest(flatCandles(59), minScore = 0).isEmpty())
    }
}
