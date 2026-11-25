package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketWatchFormatterTest {
    @Test
    fun formatsPricesAndActivityMetricsInPersian() {
        val row = MarketWatchRow("فولاد", 105.0, 104.0, 4.0, 1200.0, 124800.0, 35.0, "1")
        val output = MarketWatchFormatter.format(listOf(row))
        assertTrue(Regex("تعداد ردیف معتبر: 1\\s*\\|\\s*نمایش: 1").containsMatchIn(output))
        assertTrue(output.contains("آخرین: 105"))
        assertTrue(output.contains("پایانی: 104"))
        assertTrue(output.contains("تغییر: +4.00%"))
        assertTrue(output.contains("حجم: 1200"))
        assertTrue(output.contains("ارزش: 124800"))
        assertTrue(output.contains("تعداد معاملات: 35"))
    }

    @Test
    fun marksLowTradeCountAndShowsMissingValues() {
        val row = MarketWatchRow("نماد", 100.0, null, null, null, null, 5.0, null)
        val output = MarketWatchFormatter.format(listOf(row))
        assertTrue(output.contains("پایانی: —"))
        assertTrue(output.contains("تغییر: ناموجود"))
        assertTrue(output.contains("حجم: ناموجود"))
        assertTrue(output.contains("ارزش: ناموجود"))
        assertTrue(output.contains("تعداد معاملات: 5"))
        assertTrue(output.contains("هشدار: معاملات بسیار کم"))
    }

    @Test
    fun respectsDisplayLimitWithoutChangingValidRowCount() {
        val rows = (1..3).map {
            MarketWatchRow("نماد$it", 100.0, 100.0, 0.0, 1.0, 1.0, 1.0, it.toString())
        }
        val output = MarketWatchFormatter.format(rows, 2)
        assertTrue(Regex("تعداد ردیف معتبر: 3\\s*\\|\\s*نمایش: 2").containsMatchIn(output))
        assertEquals(2, Regex("\\d+\\. نماد").findAll(output).count())
    }
}
