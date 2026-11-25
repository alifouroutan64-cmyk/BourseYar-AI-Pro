package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Test

class CodalSearchRequestTest {
    @Test
    fun buildsBothSupportedSearchShapesAndEncodesSymbol() {
        val urls = CodalSearchRequest.urls(" نماد آزمایشی ", 20)

        assertEquals(2, urls.size)
        assertEquals(
            "https://search.codal.ir/api/search/v2/q?search=%D9%86%D9%85%D8%A7%D8%AF+%D8%A2%D8%B2%D9%85%D8%A7%DB%8C%D8%B4%DB%8C&PageNumber=1&PageSize=20",
            urls[0]
        )
        assertEquals(
            "https://search.codal.ir/api/search/v2/q?Symbol=%D9%86%D9%85%D8%A7%D8%AF+%D8%A2%D8%B2%D9%85%D8%A7%DB%8C%D8%B4%DB%8C&PageNumber=1&PageSize=20",
            urls[1]
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankSymbol() {
        CodalSearchRequest.urls("   ", 20)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOverlongSymbol() {
        CodalSearchRequest.urls("ن".repeat(CodalSearchRequest.MAX_SYMBOL_LENGTH + 1), 20)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsControlCharactersInSymbol() {
        CodalSearchRequest.urls("نماد\nآزمایشی", 20)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroPageSize() {
        CodalSearchRequest.urls("نماد", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPageSizeAboveMaximum() {
        CodalSearchRequest.urls("نماد", CodalSearchRequest.MAX_PAGE_SIZE + 1)
    }

    @Test
    fun acceptsMaximumPageSize() {
        val urls = CodalSearchRequest.urls("نماد", CodalSearchRequest.MAX_PAGE_SIZE)

        assertEquals(true, urls.all { it.endsWith("PageSize=100") })
    }

    @Test
    fun acceptsMaximumSymbolLength() {
        val urls = CodalSearchRequest.urls("ن".repeat(CodalSearchRequest.MAX_SYMBOL_LENGTH), 20)

        assertEquals(2, urls.size)
    }
}
