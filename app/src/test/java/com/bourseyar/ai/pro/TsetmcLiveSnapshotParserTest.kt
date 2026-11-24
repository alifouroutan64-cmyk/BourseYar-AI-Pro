package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TsetmcLiveSnapshotParserTest {
    private val quote = """{"closingPriceInfo":{
        "pDrCotVal":1050,"pClosing":1000,"qTotTran5J":25000,"zTotTran":120,
        "hEven": "123045","instrumentState":{"cEtavalTitle":"مجاز"}
    }}"""
    private val book = """{"bestLimits":[
        {"number":1,"pd":1040,"qd":5000,"zd":12,"po":1050,"qo":4000,"zo":10},
        {"number":2,"pd":1030,"qd":3000,"zd":8,"po":1060,"qo":2000,"zo":6}
    ]}"""
    private val trades = """{"trade":[
        {"hEven":"123100","pTran":1050,"qTitTran":100,"nTran":2},
        {"hEven":"122900","pTran":1040,"qTitTran":50,"nTran":1}
    ]}"""
    private val flow = """{"clientType":{
        "buy_I_Volume":900,"sell_I_Volume":400,
        "buy_N_Volume":200,"sell_N_Volume":100,
        "buy_CountI":9,"sell_CountI":4
    }}"""

    @Test
    fun parsesQuoteOrderBookTradesAndClientFlow() {
        val result = TsetmcLiveSnapshotParser.parse("فولاد", quote, book, trades, flow)
        assertEquals("فولاد", result.symbol)
        assertEquals(1050.0, result.quote.last!!, 0.001)
        assertEquals(5.0, result.quote.changePercent!!, 0.001)
        assertEquals(2, result.orderBook.size)
        assertEquals(5000.0, result.orderBook.first().bidVolume!!, 0.001)
        assertEquals(2, result.trades.size)
        assertEquals("123100", result.trades.first().time)
        assertEquals(500.0, result.clientFlow!!.netIndividualVolume!!, 0.001)
        assertEquals(500.0 / 9.0, result.clientFlow.individualBuyPerBuyer!!, 0.001)
    }

    @Test
    fun toleratesUnavailableOptionalClientFlow() {
        val result = TsetmcLiveSnapshotParser.parse("فولاد", quote, book, trades, null)
        assertNull(result.clientFlow)
    }

    @Test
    fun toleratesMalformedOptionalClientFlow() {
        val result = TsetmcLiveSnapshotParser.parse("فولاد", quote, book, trades, "not-json")
        assertNull(result.clientFlow)
    }

    @Test
    fun returnsEmptyListsWhenBookAndTradesAreEmpty() {
        val result = TsetmcLiveSnapshotParser.parse(
            "فولاد", quote, """{"bestLimits":[]}""", """{"trade":[]}""", null
        )
        assertEquals(0, result.orderBook.size)
        assertEquals(0, result.trades.size)
    }

    @Test
    fun rejectsQuoteWithoutExpectedObject() {
        assertThrows(IllegalStateException::class.java) {
            TsetmcLiveSnapshotParser.parse("فولاد", """{}""", book, trades, null)
        }
    }
}
