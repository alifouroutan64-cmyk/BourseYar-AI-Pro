package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TsetmcFundamentalMatchingTest {
    @Test
    fun selectsExactSymbolInsteadOfFirstSearchResult() {
        val instruments = listOf("فولادح" to "wrong-1", "فولاد" to "right-2")
        assertEquals("right-2", selectExactTsetmcInstrumentCode(instruments, "فولاد"))
    }

    @Test
    fun matchesArabicAndPersianLetterVariants() {
        val instruments = listOf("\u0643\u064A\u0627\u0646" to "123")
        assertEquals("123", selectExactTsetmcInstrumentCode(instruments, "کیان"))
    }

    @Test
    fun rejectsPrefixAndCompanyNameOnlyMatches() {
        val instruments = listOf("فولادح" to "1")
        assertNull(selectExactTsetmcInstrumentCode(instruments, "فولاد"))
    }

    @Test
    fun ignoresRowsWithoutUsableInstrumentCode() {
        val instruments = listOf("فولاد" to "", "فولاد" to "null")
        assertNull(selectExactTsetmcInstrumentCode(instruments, "فولاد"))
    }
}
