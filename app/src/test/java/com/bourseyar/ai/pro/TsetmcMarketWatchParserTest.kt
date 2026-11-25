package com.bourseyar.ai.pro

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TsetmcMarketWatchParserTest {
    private fun row(symbol: String, id: String, last: Int = 105) = JSONObject()
        .put("lVal18AFC", symbol)
        .put("yVal", 300)
        .put("pDrCotVal", last)
        .put("pClosing", 104)
        .put("priceYesterday", 100)
        .put("qTotTran5J", 1200)
        .put("qTotCap", 124800)
        .put("zTotTran", 35)
        .put("insCode", id)

    @Test
    fun parsesTypedRowsAndPreservesMarketActivity() {
        val result = TsetmcMarketWatchParser.parse(JSONArray().put(row("فولاد", "1")))
        val item = result.rows.single()
        assertEquals("فولاد", item.symbol)
        assertEquals(105.0, item.last, 0.0)
        assertEquals(104.0, item.close!!, 0.0)
        assertEquals(1200.0, item.volume!!, 0.0)
        assertEquals(124800.0, item.tradedValue!!, 0.0)
        assertEquals(35.0, item.tradeCount!!, 0.0)
    }

    @Test
    fun excludesNonCommonStocksAndDeduplicatesSameInstrumentId() {
        val nonCommon = row("اختیار", "2").put("yVal", 400)
        val result = TsetmcMarketWatchParser.parse(
            JSONArray().put(row("فولاد", "1")).put(row("فولاد", "1")).put(nonCommon)
        )
        assertEquals(1, result.rows.size)
        assertEquals("1", result.rows.single().instrumentId)
        assertTrue(result.diagnostics.contains("تکراری‌های حذف‌شده"))
    }

    @Test
    fun keepsMissingActivityAsNullAndRejectsEmptyValidRows() {
        val sparse = JSONObject().put("lVal18AFC", "نماد").put("yVal", 300).put("pDrCotVal", 100)
        val result = TsetmcMarketWatchParser.parse(JSONArray().put(sparse))
        assertNull(result.rows.single().volume)
        assertNull(result.rows.single().tradedValue)
        assertNull(result.rows.single().tradeCount)
        assertEquals(emptyList<MarketWatchRow>(), TsetmcMarketWatchParser.parse(JSONArray()).rows)
    }

    @Test
    fun preservesRowsWhenInstrumentIdIsMissingEvenIfRowsAreIdentical() {
        val withoutId = JSONObject()
            .put("lVal18AFC", "نماد")
            .put("yVal", 300)
            .put("pDrCotVal", 100)
        val result = TsetmcMarketWatchParser.parse(
            JSONArray().put(withoutId).put(JSONObject(withoutId.toString()))
        )
        assertEquals(2, result.rows.size)
        assertTrue(result.rows.all { it.instrumentId == null })
    }

    @Test
    fun rejectsOverflowingChangePercentage() {
        val extreme = JSONObject()
            .put("lVal18AFC", "نماد")
            .put("yVal", 300)
            .put("pDrCotVal", Double.MAX_VALUE)
            .put("priceYesterday", Double.MIN_VALUE)
        val result = TsetmcMarketWatchParser.parse(JSONArray().put(extreme))
        assertNull(result.rows.single().changePercent)
    }

    @Test
    fun ignoresClosingPriceAliasThatIsFarFromLastPrice() {
        val result = TsetmcMarketWatchParser.parse(JSONArray().put(row("فولاد", "1").put("pClosing", 200)))
        assertNull(result.rows.single().close)
        assertEquals(5.0, result.rows.single().changePercent!!, 0.0001)
    }
}
