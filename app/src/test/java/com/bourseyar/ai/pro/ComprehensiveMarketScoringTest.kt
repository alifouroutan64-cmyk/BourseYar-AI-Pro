package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ComprehensiveMarketScoringTest {
    @Test fun missingFundamentalsAreNotScored() {
        assertNull(ComprehensiveMarketScoring.fundamentalScore(null))
        assertNull(ComprehensiveMarketScoring.fundamentalScore(FundamentalSnapshot("ABC", 0, emptyList())))
    }

    @Test fun positiveFundamentalsIncreaseScore() {
        val snapshot = FundamentalSnapshot(
            symbol = "ABC", filingsFound = 0, recentFilingTitles = emptyList(),
            eps = 10.0, pe = 10.0, revenueGrowthPercent = 20.0,
            netProfitGrowthPercent = 15.0, roePercent = 20.0, debtToEquity = 0.5
        )
        assertEquals(100, ComprehensiveMarketScoring.fundamentalScore(snapshot))
    }

    @Test fun negativeFundamentalsReduceScoreAndClampAtZero() {
        val snapshot = FundamentalSnapshot(
            symbol = "ABC", filingsFound = 0, recentFilingTitles = emptyList(),
            eps = -1.0, pe = 40.0, revenueGrowthPercent = -20.0,
            netProfitGrowthPercent = -15.0, roePercent = -2.0, debtToEquity = 3.0
        )
        assertEquals(0, ComprehensiveMarketScoring.fundamentalScore(snapshot))
    }

    @Test fun compositeScoreNormalizesOnlyAvailableFactors() {
        assertEquals(80, ComprehensiveMarketScoring.compositeScore(80, null, null, null))
        assertEquals(75, ComprehensiveMarketScoring.compositeScore(80, 80, 1.0, 80))
    }

    @Test fun relativeVolumeIsConvertedToZeroToHundredAndCapped() {
        assertEquals(100, ComprehensiveMarketScoring.compositeScore(100, null, 5.0, null))
        assertEquals(0, ComprehensiveMarketScoring.compositeScore(0, null, 0.0, null))
    }

    @Test fun invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ComprehensiveMarketScoring.compositeScore(101, null, null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ComprehensiveMarketScoring.compositeScore(50, null, -1.0, null)
        }
    }
}
