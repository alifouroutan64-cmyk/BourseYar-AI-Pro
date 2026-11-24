package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TsetmcDailyHistoryParserTest {
    private fun row(date: String, high: Int = 105, low: Int = 95, open: Int = 100,
                    last: Int = 102, volume: Int = 1000, close: Int = 101) =
        "$date,$high,$low,$open,$last,$volume,$close"

    @Test
    fun parsesValidRowsInChronologicalOrder() {
        val raw = row("20260102") + ";" + row("20260101")
        val result = TsetmcDailyHistoryParser.parse(" فولاد ", raw, minimumCandles = 2)
        assertEquals("فولاد", result.symbol)
        assertEquals(2, result.candles.size)
        assertEquals(101.0, result.candles.first().close, 0.001)
        assertEquals(1000.0, result.candles.first().volume, 0.001)
    }

    @Test
    fun skipsInvalidDatesAndMalformedRows() {
        val raw = row("20260230") + ";" + "not-a-row" + ";" + row("20260101")
        val result = TsetmcDailyHistoryParser.parse("فولاد", raw, minimumCandles = 1)
        assertEquals(1, result.candles.size)
    }

    @Test
    fun rejectsInvalidOhlcAndNegativeVolume() {
        val invalidHigh = row("20260101", high = 99)
        val negativeVolume = row("20260102", volume = -1)
        assertThrows(IllegalArgumentException::class.java) {
            TsetmcDailyHistoryParser.parse("فولاد", "$invalidHigh;$negativeVolume", minimumCandles = 1)
        }
    }

    @Test
    fun rejectsDuplicateDates() {
        val raw = row("20260101") + ";" + row("20260101", close = 103)
        assertThrows(IllegalArgumentException::class.java) {
            TsetmcDailyHistoryParser.parse("فولاد", raw, minimumCandles = 2)
        }
    }

    @Test
    fun enforcesMinimumHistoryLength() {
        assertThrows(IllegalArgumentException::class.java) {
            TsetmcDailyHistoryParser.parse("فولاد", row("20260101"), minimumCandles = 50)
        }
    }
}
