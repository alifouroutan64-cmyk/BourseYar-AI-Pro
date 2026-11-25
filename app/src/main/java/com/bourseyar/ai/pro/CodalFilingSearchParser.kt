package com.bourseyar.ai.pro

import org.json.JSONArray
import org.json.JSONObject

/**
 * Defensive, pure parsing for Codal's public filing-search response.
 * Returns null only when none of the supported response-array keys exists.
 */
internal object CodalFilingSearchParser {
    fun parse(json: String, symbol: String): FundamentalSnapshot? {
        val root = JSONObject(json)
        val rows = root.optJSONArray("Letters")
            ?: root.optJSONArray("letter")
            ?: root.optJSONArray("items")
            ?: root.optJSONArray("data")
            ?: return null

        val normalizedRequested = normalizeSymbol(symbol)
        val references = mutableListOf<CodalFilingReference>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val title = firstText(row, "Title", "title", "LetterTitle", "letterTitle") ?: continue
            val rowSymbol = firstText(row, "Symbol", "symbol", "Ticker", "ticker")
            if (rowSymbol != null && normalizeSymbol(rowSymbol) != normalizedRequested) continue

            val safeUrl = safeCodalUrl(firstText(row, "HtmlUrl", "htmlUrl", "Url", "url", "Link", "link"))
            val safeExcelUrl = safeCodalExcelUrl(firstText(row, "ExcelUrl", "excelUrl", "ExcelURL", "excelURL"))
            val date = firstText(
                row,
                "PublishDateTime", "publishDateTime", "SentDateTime", "sentDateTime", "PublishDate"
            )
            references += CodalFilingReference(title, date, safeUrl, excelUrl = safeExcelUrl)
        }

        val total = firstNonNegativeInt(root, "Total", "total", "TotalCount", "totalCount", "Count", "count")
            ?: rows.length()
        return FundamentalSnapshot(
            symbol = symbol,
            filingsFound = maxOf(total, references.size),
            recentFilingTitles = references.take(5).map { it.title },
            recentFilings = references.take(5)
        )
    }

    private fun firstText(obj: JSONObject, vararg keys: String): String? =
        keys.asSequence()
            .map { obj.optString(it).trim() }
            .firstOrNull { it.isNotBlank() && it != "null" }

    private fun firstNonNegativeInt(obj: JSONObject, vararg keys: String): Int? =
        keys.asSequence()
            .mapNotNull { obj.opt(it)?.toString()?.toIntOrNull() }
            .firstOrNull { it >= 0 }

    private fun normalizeSymbol(value: String): String =
        value.trim().replace("ي", "ی").replace("ك", "ک")
}
