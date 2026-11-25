package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketRiskLevelsTest {
    @Test
    fun calculatesPercentageStopAndTargetsWithoutSupport() {
        val levels = MarketRiskLevels.calculate(lastPrice = 100.0, support = null, riskPercent = 5.0)

        assertEquals(95.0, levels.stopLoss, 0.0001)
        assertEquals(107.5, levels.target1, 0.0001)
        assertEquals(110.0, levels.target2, 0.0001)
    }

    @Test
    fun choosesLowerOfSupportAndPercentageStop() {
        val belowStop = MarketRiskLevels.calculate(lastPrice = 100.0, support = 92.0, riskPercent = 5.0)
        val aboveStop = MarketRiskLevels.calculate(lastPrice = 100.0, support = 98.0, riskPercent = 5.0)

        assertEquals(92.0, belowStop.stopLoss, 0.0001)
        assertEquals(112.0, belowStop.target1, 0.0001)
        assertEquals(116.0, belowStop.target2, 0.0001)
        assertEquals(95.0, aboveStop.stopLoss, 0.0001)
    }

    @Test
    fun ignoresInvalidOrAboveMarketSupport() {
        val invalid = MarketRiskLevels.calculate(lastPrice = 100.0, support = Double.NaN, riskPercent = 5.0)
        val nonPositive = MarketRiskLevels.calculate(lastPrice = 100.0, support = -10.0, riskPercent = 5.0)
        val aboveMarket = MarketRiskLevels.calculate(lastPrice = 100.0, support = 110.0, riskPercent = 5.0)

        assertEquals(95.0, invalid.stopLoss, 0.0001)
        assertEquals(95.0, nonPositive.stopLoss, 0.0001)
        assertEquals(95.0, aboveMarket.stopLoss, 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveLastPrice() {
        MarketRiskLevels.calculate(lastPrice = 0.0, support = null, riskPercent = 5.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFiniteRiskPercent() {
        MarketRiskLevels.calculate(lastPrice = 100.0, support = null, riskPercent = Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRiskPercentAboveOneHundred() {
        MarketRiskLevels.calculate(lastPrice = 100.0, support = null, riskPercent = 100.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeRiskPercent() {
        MarketRiskLevels.calculate(lastPrice = 100.0, support = null, riskPercent = -0.1)
    }

    @Test
    fun preservesCalculationForZeroRiskPercent() {
        val levels = MarketRiskLevels.calculate(lastPrice = 250.0, support = null, riskPercent = 0.0)

        assertEquals(250.0, levels.stopLoss, 0.0)
        assertEquals(250.0, levels.target1, 0.0)
        assertEquals(250.0, levels.target2, 0.0)
    }
}
