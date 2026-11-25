package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONObject

class TsetmcFundamentalMatchingTest {
    @Test
    fun selectsExactSymbolInsteadOfFirstSearchResult() {
        val instruments = listOf("فولادح" to "123456", "فولاد" to "987654")
        assertEquals("right-2", selectExactTsetmcInstrumentCode(instruments, "فولاد"))
    }

    @Test
    fun matchesArabicAndPersianLetterVariants() {
        val instruments = listOf("\u0643\u064A\u0627\u0646" to "123456")
        assertEquals("123", selectExactTsetmcInstrumentCode(instruments, "کیان"))
    }

    @Test
    fun rejectsPrefixAndCompanyNameOnlyMatches() {
        val instruments = listOf("فولادح" to "1")
        assertNull(selectExactTsetmcInstrumentCode(instruments, "فولاد"))
    }

    @Test
    fun ignoresRowsWithoutUsableInstrumentCode() {
        val instruments = listOf("فولاد" to "", "فولاد" to "null", "فولاد" to "12/345")
        assertNull(selectExactTsetmcInstrumentCode(instruments, "فولاد"))
    }

    @Test
    fun rejectsMalformedInstrumentCodes() {
        val instruments = listOf("فولاد" to "123/456", "فولاد" to "1234", "فولاد" to "12345678901234567890123456")
        assertNull(selectExactTsetmcInstrumentCode(instruments, "فولاد"))
    }

    @Test
    fun parsesFundamentalAliasesAndKeepsMissingValuesNull() {
        val parsed = TsetmcFundamentalApi.parseInstrumentInfo(
            JSONObject("""{"instrumentInfo":{"EPS":"1,250","peRatio":6.5,"sectorName":"فلزات"}}""")
        )
        assertEquals(1250.0, parsed.eps!!, 0.001)
        assertEquals(6.5, parsed.pe!!, 0.001)
        assertEquals("فلزات", parsed.sector)
        val missing = TsetmcFundamentalApi.parseInstrumentInfo(JSONObject("""{}"""))
        assertNull(missing.eps)
        assertNull(missing.pe)
        assertNull(missing.sector)
    }


}
