package com.bourseyar.ai.pro

import java.net.URLEncoder

/**
 * Builds the two supported Codal search URL shapes after validating user input.
 * Keeping URL construction pure makes request limits and encoding testable.
 */
internal object CodalSearchRequest {
    private const val BASE = "https://search.codal.ir/api/search/v2/q"
    const val MAX_PAGE_SIZE = 100

    fun urls(symbol: String, limit: Int): List<String> {
        val normalizedSymbol = symbol.trim()
        require(normalizedSymbol.isNotEmpty()) { "symbol must not be blank" }
        require(limit in 1..MAX_PAGE_SIZE) { "limit must be between 1 and $MAX_PAGE_SIZE" }

        val encoded = URLEncoder.encode(normalizedSymbol, "UTF-8")
        return listOf(
            "${BASE}?search=${encoded}&PageNumber=1&PageSize=${limit}",
            "${BASE}?Symbol=${encoded}&PageNumber=1&PageSize=${limit}"
        )
    }
}
