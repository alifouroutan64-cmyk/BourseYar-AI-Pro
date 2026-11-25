package com.bourseyar.ai.pro

import java.util.Locale

/** Formats each market symbol as short, readable Persian lines for narrow phone screens. */
object MarketWatchFormatter {
    fun format(rows: List<MarketWatchRow>, limit: Int = 45): String {
        require(limit >= 0) { "limit must not be negative" }
        val shown = rows.take(limit)
        val lines = shown.mapIndexed { index, item ->
            val lowTradeWarning = item.tradeCount != null && item.tradeCount < 20.0
            val changeText = item.changePercent?.let { "%+.2f%%".format(Locale.US, it) } ?: "ناموجود"
            buildString {
                append(index + 1).append(". ").append(item.symbol).append("\n")
                append("آخرین: ").append("%.0f".format(Locale.US, item.last))
                append("  |  پایانی: ").append(item.close?.let { "%.0f".format(Locale.US, it) } ?: "—").append("\n")
                append("تغییر: ").append(changeText).append("\n")
                append("حجم: ").append(item.volume?.let { "%.0f".format(Locale.US, it) } ?: "ناموجود").append("\n")
                append("ارزش: ").append(item.tradedValue?.let { "%.0f".format(Locale.US, it) } ?: "ناموجود")
                append("  |  تعداد معاملات: ").append(item.tradeCount?.let { "%.0f".format(Locale.US, it) } ?: "ناموجود")
                if (lowTradeWarning) append("\nهشدار: معاملات بسیار کم")
            }
        }
        return "تعداد ردیف معتبر: ${rows.size}  |  نمایش: ${shown.size}\n\n" + lines.joinToString("\n\n")
    }
}
