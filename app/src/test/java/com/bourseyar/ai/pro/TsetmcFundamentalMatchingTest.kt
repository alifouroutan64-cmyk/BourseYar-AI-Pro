package com.bourseyar.ai.pro

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TsetmcFundamentalMatchingTest {
    @Test
    fun selectsExactSymbolInsteadOfFirstSearchResult() {
        val rows = JSONArray(
            """[
                {"insCode":"wrong-1","lVal18AFC":"فولادح"},
                {"insCode":"right-2","lVal18AFC":"فولاد"}
            ]"""
        )
        assertEquals("right-2", selectExactTsetmcInstrumentCode(rows, "فولاد"))
    }

    @Test
    fun matchesArabicAndPersianLetterVariants() {
        val rows = JSONArray(
            """[{"insCode":"123","lVal18AFC":"فولاد"}]"""
        )
        assertEquals("123", selectExactTsetmcInstrumentCode(rows, "فولاد"))
    }

    @Test
    fun rejectsPrefixAndCompanyNameOnlyMatches() {
        val rows = JSONArray(
            """[
                {"insCode":"1","lVal18AFC":"فولادح"},
                {"insCode":"2","lVal30":"شرکت فولاد مبارکه"}
            ]"""
        )
        assertNull(selectExactTsetmcInstrumentCode(rows, "فولاد"))
    }

    @Test
    fun ignoresRowsWithoutUsableInstrumentCode() {
        val rows = JSONArray(
            """[
                {"insCode":"","lVal18AFC":"فولاد"},
                {"lVal18AFC":"فولاد"}
            ]"""
        )
        assertNull(selectExactTsetmcInstrumentCode(rows, "فولاد"))
    }
}
