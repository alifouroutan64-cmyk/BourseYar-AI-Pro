package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TsetmcClientFlowHistoryParserTest {
    @Test
    fun parsesAndSortsClientFlowHistory() {
        val body = """{"clientType":[
            {"recDate":"20240103","buy_I_Volume":900,"sell_I_Volume":400,"buy_N_Volume":200,"sell_N_Volume":100,"buy_I_Value":9000,"sell_I_Value":4000,"buy_I_Count":9,"sell_I_Count":4},
            {"recDate":"20240102","buy_I_Volume":500,"sell_I_Volume":600,"buy_N_Volume":100,"sell_N_Volume":90,"buy_I_Value":5000,"sell_I_Value":6000,"buy_I_Count":5,"sell_I_Count":6}
        ]}"""
        val result = TsetmcClientFlowHistoryParser.parse(body)
        assertEquals(listOf("20240102", "20240103"), result.map { it.date })
        assertEquals(500.0, result.last().netIndividualVolume!!, 0.001)
        assertEquals(5000.0, result.last().netIndividualValue!!, 0.001)
        assertEquals(13000.0, result.last().grossIndividualValue!!, 0.001)
        assertEquals(100.0, result.last().individualBuyPerBuyer!!, 0.001)
    }

    @Test
    fun keepsMissingValuesUnknown() {
        val body = """{"clientType":[{"recDate":"20240103","buy_I_Volume":10}]}"""
        val result = TsetmcClientFlowHistoryParser.parse(body).single()
        assertNull(result.netIndividualVolume)
        assertNull(result.netIndividualValue)
        assertNull(result.individualBuyPerBuyer)
    }

    @Test
    fun removesDuplicateDatesAndInvalidDateRows() {
        val body = """{"clientType":[
            {"recDate":"20240103","buy_I_Volume":10,"sell_I_Volume":2},
            {"recDate":"20240103","buy_I_Volume":20,"sell_I_Volume":2},
            {"recDate":"bad","buy_I_Volume":10,"sell_I_Volume":2}
        ]}"""
        val result = TsetmcClientFlowHistoryParser.parse(body)
        assertEquals(1, result.size)
        assertEquals(8.0, result.single().netIndividualVolume!!, 0.001)
    }

    @Test
    fun rejectsMissingHistoryArrayAndEmptyValidHistory() {
        assertThrows(IllegalStateException::class.java) {
            TsetmcClientFlowHistoryParser.parse("""{}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TsetmcClientFlowHistoryParser.parse("""{"clientType":[{"recDate":"bad"}]}""")
        }
    }
}
