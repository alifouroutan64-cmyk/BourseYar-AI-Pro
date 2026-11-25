package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TsetmcInstrumentResolverTest {
    @Test
    fun resolvesExactSymbolAndNormalizesPersianArabicLetters() {
        val json = """{"instrumentSearch":[
            {"lVal18AFC":"فولادح","insCode":"11111"},
            {"lVal18AFC":"فولاد","insCode":"123456789"}
        ]}"""
        assertEquals("123456789", TsetmcInstrumentResolver.resolve(" فولاد ", json))
    }

    @Test
    fun doesNotAcceptPartialOrDifferentSymbolMatches() {
        val json = """{"instrumentSearch":[
            {"lVal18AFC":"فولادح","insCode":"11111"}
        ]}"""
        assertNull(TsetmcInstrumentResolver.resolve("فولاد", json))
    }

    @Test
    fun returnsNullForEmptySearchResults() {
        assertNull(TsetmcInstrumentResolver.resolve("فولاد", """{"instrumentSearch":[]}"""))
    }

    @Test
    fun rejectsMalformedResponseStructure() {
        assertThrows(IllegalStateException::class.java) {
            TsetmcInstrumentResolver.resolve("فولاد", """{"items":[]}""")
        }
    }

    @Test
    fun rejectsInvalidInstrumentCode() {
        val json = """{"instrumentSearch":[{"lVal18AFC":"فولاد","insCode":"abc"}]}"""
        assertThrows(IllegalArgumentException::class.java) {
            TsetmcInstrumentResolver.resolve("فولاد", json)
        }
    }

    @Test
    fun skipsMalformedDuplicateAndUsesNextValidExactMatch() {
        val json = """{"instrumentSearch":[
            {"lVal18AFC":"فولاد","insCode":"abc"},
            {"lVal18AFC":"فولاد","insCode":"123456789"}
        ]}"""
        assertEquals("123456789", TsetmcInstrumentResolver.resolve("فولاد", json))
    }

    @Test
    fun rejectsWhenAllExactMatchesHaveInvalidCodes() {
        val json = """{"instrumentSearch":[
            {"lVal18AFC":"فولاد","insCode":"abc"},
            {"lVal18AFC":"فولاد","insCode":"123"}
        ]}"""
        assertThrows(IllegalArgumentException::class.java) {
            TsetmcInstrumentResolver.resolve("فولاد", json)
        }
    }
}
