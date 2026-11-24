package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TsetmcMarketLeadersParserTest {
    @Test
    fun filtersCommonSharesAndSortsByTradedValue() {
        val body = """{"marketwatch":[
            {"insCode":"1","lva":"الف","yVal":300,"qTotTran5J":100,"qTotCap":2000},
            {"insCode":"2","lva":"ب","yVal":300,"qTotTran5J":200,"qTotCap":5000},
            {"insCode":"3","lva":"ج","yVal":400,"qTotTran5J":900,"qTotCap":9000}
        ]}"""
        val result = TsetmcMarketLeadersParser.parse(body)
        assertEquals(listOf("ب", "الف"), result.map { it.symbol })
        assertEquals(5000.0, result.first().tradedValue, 0.001)
    }

    @Test
    fun supportsCompactAliasesAndNumericStrings() {
        val body = """{"marketwatch":[
            {"insCode":"12345","lVal18AFC":"نماد","yval":"300","qtj":"1,200","qtc":"8,500"}
        ]}"""
        val result = TsetmcMarketLeadersParser.parse(body)
        assertEquals(1, result.size)
        assertEquals(1200.0, result[0].volume, 0.001)
        assertEquals(8500.0, result[0].tradedValue, 0.001)
    }

    @Test
    fun skipsRowsWithMissingOrNonPositiveValues() {
        val body = """{"marketwatch":[
            {"insCode":"1","lva":"الف","yVal":300,"qTotTran5J":0,"qTotCap":2000},
            {"insCode":"2","lva":"ب","yVal":300,"qTotTran5J":100,"qTotCap":-1}
        ]}"""
        assertThrows(IllegalArgumentException::class.java) {
            TsetmcMarketLeadersParser.parse(body)
        }
    }

    @Test
    fun validatesLimitAndMissingMarketWatch() {
        assertThrows(IllegalArgumentException::class.java) {
            TsetmcMarketLeadersParser.parse("""{"marketwatch":[]}""", 0)
        }
        assertThrows(IllegalStateException::class.java) {
            TsetmcMarketLeadersParser.parse("""{}""")
        }
    }
}
