package com.bourseyar.ai.pro

import java.util.Locale

/**
 * Conservative parser for Codal's modern Excel downloads, which are commonly
 * delivered as Microsoft Office HTML tables. It intentionally returns null
 * for metrics whose statement rows or comparison values cannot be identified.
 */
data class CodalFinancialMetrics(
    val revenueGrowthPercent: Double? = null,
    val netProfitGrowthPercent: Double? = null,
    val returnOnEquityPercent: Double? = null,
    val debtToEquity: Double? = null,
    val totalRevenue: Double? = null,
    val netProfit: Double? = null,
    val totalAssets: Double? = null,
    val totalEquity: Double? = null,
    val totalLiabilities: Double? = null,
    val extractedRows: Int = 0
) {
    val hasMetrics: Boolean
        get() = listOf(revenueGrowthPercent, netProfitGrowthPercent, returnOnEquityPercent, debtToEquity).any { it != null }
}

object CodalFinancialParser {
    private data class FinancialRow(val label: String, val values: List<Double>)

    /** Returns empty metrics for non-HTML/legacy workbooks; never guesses binary XLS contents. */
    fun parseHtml(html: String): CodalFinancialMetrics {
        if (html.isBlank() || !html.contains("<table", ignoreCase = true)) return CodalFinancialMetrics()
        val rows = parseRows(html)
        if (rows.isEmpty()) return CodalFinancialMetrics()

        val revenue = findRow(rows, listOf("درآمدهای عملیاتی", "درآمد عملیاتی", "فروش خالص"))
        val profit = findRow(rows, listOf("سود (زیان) خالص", "سود(زیان) خالص", "سود خالص"))
        val assets = findRow(rows, listOf("جمع دارایی ها", "جمع دارایی"))
        val equity = findRow(rows, listOf("جمع حقوق مالکانه", "جمع حقوق صاحبان سهام", "جمع حقوق صاحبان سرمایه"))
        val liabilities = findRow(rows, listOf("جمع بدهی ها", "جمع بدهی"))

        val revenueValues = revenue?.values.orEmpty()
        val profitValues = profit?.values.orEmpty()
        val assetValues = assets?.values.orEmpty()
        val equityValues = equity?.values.orEmpty()
        val liabilityValues = liabilities?.values.orEmpty()

        return CodalFinancialMetrics(
            revenueGrowthPercent = growth(revenueValues),
            netProfitGrowthPercent = growth(profitValues),
            returnOnEquityPercent = ratioPercent(profitValues.firstOrNull(), equityValues.firstOrNull()),
            debtToEquity = ratio(liabilityValues.firstOrNull(), equityValues.firstOrNull()),
            totalRevenue = revenueValues.firstOrNull(),
            netProfit = profitValues.firstOrNull(),
            totalAssets = assetValues.firstOrNull(),
            totalEquity = equityValues.firstOrNull(),
            totalLiabilities = liabilityValues.firstOrNull(),
            extractedRows = listOfNotNull(revenue, profit, assets, equity, liabilities).size
        )
    }

    private fun parseRows(html: String): List<FinancialRow> {
        val withoutNoise = html
            .replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), " ")
        val result = mutableListOf<FinancialRow>()
        val tableRegex = Regex("(?is)<table\\b[^>]*>(.*?)</table>")
        val rowRegex = Regex("(?is)<tr\\b[^>]*>(.*?)</tr>")
        val cellRegex = Regex("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>")
        for (table in tableRegex.findAll(withoutNoise)) {
            for (row in rowRegex.findAll(table.groupValues[1])) {
                val cells = cellRegex.findAll(row.groupValues[1]).map { cleanCell(it.groupValues[1]) }.toList()
                if (cells.isEmpty()) continue
                val labelIndex = cells.indexOfFirst { it.isNotBlank() }
                if (labelIndex < 0) continue
                val label = cells[labelIndex]
                val numbers = cells.drop(labelIndex + 1).mapNotNull(::parseNumber)
                if (label.isNotBlank()) result += FinancialRow(label, numbers)
            }
        }
        return result
    }

    private fun cleanCell(value: String): String {
        val text = value
            .replace(Regex("(?is)<br\\s*/?>"), " ")
            .replace(Regex("(?is)<[^>]+>"), " ")
            .replace("&nbsp;", " ", ignoreCase = true)
            .replace("&#160;", " ")
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#39;", "'")
            .replace("ي", "ی")
            .replace("ك", "ک")
            .replace("\u200c", " ")
        return decodeNumericEntities(text).replace(Regex("\\s+"), " ").trim()
    }

    private fun decodeNumericEntities(input: String): String =
        Regex("&#(x[0-9a-fA-F]+|[0-9]+);").replace(input) { match ->
            val token = match.groupValues[1]
            val codePoint = if (token.startsWith("x", true)) token.substring(1).toIntOrNull(16) else token.toIntOrNull()
            codePoint?.takeIf { it in 1..0x10ffff }?.let { String(Character.toChars(it)) } ?: match.value
        }

    private fun normalizeLabel(value: String): String = value
        .replace("ي", "ی")
        .replace("ك", "ک")
        .replace("\u200c", " ")
        .replace(Regex("[\\sـ،,:：؛;()\\[\\]]+"), "")
        .lowercase(Locale.ROOT)

    private fun findRow(rows: List<FinancialRow>, labels: List<String>): FinancialRow? {
        val normalizedLabels = labels.map(::normalizeLabel)
        return rows.firstOrNull { row ->
            val candidate = normalizeLabel(row.label)
            candidate.isNotBlank() && normalizedLabels.any { expected ->
                candidate == expected || candidate.startsWith(expected)
            } && row.values.isNotEmpty()
        }?.let { row ->
            // The rightmost two numeric cells are the period values in Codal's
            // tabular statements; leading numeric cells can be item/row numbers.
            row.copy(values = row.values.takeLast(2))
        }
    }

    private fun growth(values: List<Double>): Double? {
        if (values.size < 2) return null
        val current = values[0]
        val previous = values[1]
        if (!current.isFinite() || !previous.isFinite() || previous == 0.0) return null
        return (current - previous) / kotlin.math.abs(previous) * 100.0
            .takeIf { it.isFinite() }
    }

    private fun ratioPercent(numerator: Double?, denominator: Double?): Double? {
        if (numerator == null || denominator == null || denominator == 0.0) return null
        return (numerator / denominator * 100.0).takeIf { it.isFinite() }
    }

    private fun ratio(numerator: Double?, denominator: Double?): Double? {
        if (numerator == null || denominator == null || denominator == 0.0) return null
        return (numerator / denominator).takeIf { it.isFinite() }
    }

    internal fun parseNumber(raw: String): Double? {
        var value = raw.trim()
            .replace('۰', '0').replace('۱', '1').replace('۲', '2').replace('۳', '3')
            .replace('۴', '4').replace('۵', '5').replace('۶', '6').replace('۷', '7')
            .replace('۸', '8').replace('۹', '9')
            .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3')
            .replace('٤', '4').replace('٥', '5').replace('٦', '6').replace('٧', '7')
            .replace('٨', '8').replace('٩', '9')
            .replace("٬", "").replace(",", "").replace("،", "")
            .replace("٫", ".").replace("−", "-")
        if (value.isBlank() || value in setOf("-", "—", "–", "نامشخص", "ندارد")) return null
        val negative = value.startsWith("(") && value.endsWith(")")
        value = value.replace("(", "").replace(")", "").replace("%", "")
            .replace(Regex("[^0-9.\\-]"), "")
        if (value.isBlank() || value == "-" || value == ".") return null
        val parsed = value.toDoubleOrNull() ?: return null
        return (if (negative) -parsed else parsed).takeIf { it.isFinite() }
    }
}
