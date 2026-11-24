package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TsetmcClientFlowAllParserTest {
    @Test
    fun parsesBulkRowsAndSupportsCompactAliases() {
        val body = """{"clientTypeAllDto":[
            {"insCode":"123456","buy_I_Volume":900,"sell_I_Volume":400,"buy_N_Volume":200,"sell_N_Volume":100,"buy_CountI":9,"sell_CountI":4},
            {"insCode":"234567","buyIVolume":500,"sellIVolume":200,"buyNVolume":80,"sellNVolume":30,"buyCountI":5,"sellCountI":2}
        ]}"""
        val result = TsetmcClientFlowAllParser.parse(body)
        assertEquals(2, result.size)
        assertEquals(500.0, result["123456"]!!.netIndividualVolume!!, 0.001)
        assertEquals(100.0, result["123456"]!!.individualBuyPerBuyer!!, 0.001)
        assertEquals(300.0, result["234567"]!!.netIndividualVolume!!, 0.001)
        assertEquals(50.0, result["234567"]!!.netLegalVolume!!, 0.001)
    }

    @Test
    fun skipsRowsWithMissingRequiredFieldsOrInvalidCodes() {
        val body = """{"clientTypeAllDto":[
            {"insCode":"123456","buy_I_Volume":10,"sell_I_Volume":2,"buy_N_Volume":3},
            {"insCode":"invalid","buy_I_Volume":10,"sell_I_Volume":2,"buy_N_Volume":3,"sell_N_Volume":1}
        ]}"""
        assertThrows(IllegalArgumentException::class.java) {
            TsetmcClientFlowAllParser.parse(body)
        }
    }

    @Test
    fun keepsUnavailableBuyerCountsUnknown() {
        val body = """{"clientTypeAllDto":[
            {"insCode":"123456","buy_I_Volume":10,"sell_I_Volume":2,"buy_N_Volume":3,"sell_N_Volume":1}
        ]}"""
        val result = TsetmcClientFlowAllParser.parse(body).getValue("123456")
        assertNull(result.individualBuyPerBuyer)
        assertNull(result.individualSellPerSeller)
    }

    @Test
    fun rejectsMissingOrEmptyBulkData() {
        assertThrows(IllegalStateException::class.java) {
            TsetmcClientFlowAllParser.parse("""{}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TsetmcClientFlowAllParser.parse("""{"clientTypeAllDto":[]}""")
        }
    }
}
