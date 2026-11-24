package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CodalFinancialParserTest {
    @Test
    fun extractsGrowthAndBalanceSheetRatiosFromHtmlTables() {
        val html = """
            <html><body>
            <table>
              <tr><th>شرح</th><th>دوره جاری</th><th>دوره مشابه قبل</th></tr>
              <tr><td>درآمدهای عملیاتی</td><td>1,500</td><td>1,000</td></tr>
              <tr><td>سود (زیان) خالص</td><td>300</td><td>200</td></tr>
            </table>
            <table>
              <tr><td>جمع دارایی‌ها</td><td>5,000</td></tr>
              <tr><td>جمع حقوق مالکانه</td><td>2,000</td></tr>
              <tr><td>جمع بدهی‌ها</td><td>3,000</td></tr>
            </table>
            </body></html>
        """.trimIndent()

        val metrics = CodalFinancialParser.parseHtml(html)

        assertEquals(50.0, metrics.revenueGrowthPercent!!, 0.0001)
        assertEquals(50.0, metrics.netProfitGrowthPercent!!, 0.0001)
        assertEquals(15.0, metrics.returnOnEquityPercent!!, 0.0001)
        assertEquals(1.5, metrics.debtToEquity!!, 0.0001)
        assertEquals(5, metrics.extractedRows)
    }

    @Test
    fun parsesPersianDigitsAndNegativeParentheses() {
        assertEquals(12345.0, CodalFinancialParser.parseNumber("۱۲٬۳۴۵")!!, 0.0)
        assertEquals(-250.5, CodalFinancialParser.parseNumber("(۲۵۰٫۵)")!!, 0.0)
        assertNull(CodalFinancialParser.parseNumber("—"))
    }

    @Test
    fun doesNotInventGrowthWhenComparisonPeriodIsMissingOrZero() {
        val onePeriod = "<table><tr><td>درآمدهای عملیاتی</td><td>100</td></tr></table>"
        val zeroPrior = "<table><tr><td>درآمدهای عملیاتی</td><td>100</td><td>0</td></tr></table>"
        assertNull(CodalFinancialParser.parseHtml(onePeriod).revenueGrowthPercent)
        assertNull(CodalFinancialParser.parseHtml(zeroPrior).revenueGrowthPercent)
    }

    @Test
    fun ignoresNonHtmlAndUnrelatedTables() {
        val binary = "PK\\u0003\\u0004not-an-html-workbook"
        val unrelated = "<table><tr><td>نام شرکت</td><td>نمونه</td></tr></table>"
        assertFalse(CodalFinancialParser.parseHtml(binary).hasMetrics)
        assertFalse(CodalFinancialParser.parseHtml(unrelated).hasMetrics)
    }
}
