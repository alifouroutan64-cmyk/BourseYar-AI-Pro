package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TsetmcFiveSessionFlowScorerTest {
    private fun day(net: Double?, gross: Double?) = ClientFlowDay(
        date = "20261125",
        netIndividualVolume = null,
        netIndividualValue = net,
        grossIndividualValue = gross,
        netLegalVolume = null,
        individualBuyPerBuyer = null,
        individualSellPerSeller = null
    )

    @Test fun missingHistoryReturnsNull() {
        assertNull(TsetmcFiveSessionFlowScorer.score(emptyList()))
    }

    @Test fun missingAndInvalidDaysAreExcluded() {
        assertNull(TsetmcFiveSessionFlowScorer.score(listOf(day(null, 100.0), day(10.0, 0.0))))
    }

    @Test fun positiveAndNegativeSessionsAreAggregated() {
        val result = TsetmcFiveSessionFlowScorer.score(
            listOf(day(50.0, 100.0), day(-25.0, 100.0), day(25.0, 100.0))
        )
        assertEquals(60, result)
    }

    @Test fun onlyLatestFiveSessionsAreUsed() {
        val history = listOf(day(-100.0, 100.0)) + List(5) { day(100.0, 100.0) }
        assertEquals(100, TsetmcFiveSessionFlowScorer.score(history))
    }

    @Test fun inconsistentTotalsFailClosed() {
        assertNull(TsetmcFiveSessionFlowScorer.score(listOf(day(200.0, 100.0))))
    }
}
