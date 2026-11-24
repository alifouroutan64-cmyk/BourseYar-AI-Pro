package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketCsvParserTest {
    private fun date(day: Int) = java.time.LocalDate.of(2026, 1, 1).plusDays((day - 1).toLong()).toString()

    private fun row(date: String, close: Int = 100) =
        "فولاد,$date,99,${close + 1},98,$close,1000"

    @Test
    fun sortsRowsChronologicallyAndPreservesOpenAndVolume() {
        val rows = (1..50).reversed().joinToString("\n") { day ->
            row(date(day), close = 100 + day)
        }
        val series = MarketCsvParser.parse("symbol,date,open,high,low,close,volume\n$rows").single()
        assertEquals(50, series.candles.size)
        assertEquals(101.0, series.candles.first().close, 0.0)
        assertEquals(99.0, series.candles.first().open, 0.0)
        assertEquals(1000.0, series.candles.first().volume, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateDates() {
        val rows = (1..49).map { day -> row("2026-01-%02d".format(java.util.Locale.US, day)) }.toMutableList()
        rows += row("2026-01-01")
        MarketCsvParser.parse(rows.joinToString("\n"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidOhlc() {
        val rows = (1..50).map { day ->
            if (day == 1) "فولاد,${date(day)},99,97,98,100,1000"
            else row("2026-01-%02d".format(java.util.Locale.US, day))
        }
        MarketCsvParser.parse(rows.joinToString("\n"))
    }
}
