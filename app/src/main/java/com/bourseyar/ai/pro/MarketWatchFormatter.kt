package com.bourseyar.ai.pro

import java.util.Locale

/** Converts parsed market rows into a stable Persian dashboard text representation. */
object MarketWatchFormatter {
    fun format(rows: List<MarketWatchRow>, limit: Int = 45): String {
        require(limit >= 0) { "limit must not be negative" }
        val shown = rows.take(limit)
        val lines = shown.mapIndexed { index, item ->
            val lowTradeWarning = item.tradeCount != null && item.tradeCount < 20.0
            val changeText = item.changePercent?.let { "%+.2f%%".format(Locale.US, it) } ?: "درصد ناموجود"
            (index + 1).toString() + ". " + item.symbol +
                " | آخرین: " + "%.0f".format(Locale.US, item.last) +
                " | پایانی: " + (item.close?.let { "%.0f".format(Locale.US, it) } ?: "—") +
                " | تغییر: " + changeText + "\n" +
                "حجم: " + (item.volume?.let { "%.0f".format(Locale.US, it) } ?: "ناموجود") +
                " | ارزش: " + (item.tradedValue?.let { "%.0f".format(Locale.US, it) } ?: "ناموجود") +
                " | تعداد معاملات: " + (item.tradeCount?.let { "%.0f".format(Locale.US, it) } ?: "ناموجود") +
                (if (lowTradeWarning) " | هشدار: معاملات بسیار کم" else "")
        }
        return "تعداد ردیف معتبر: ${rows.size} | نمایش: ${shown.size}\n\n" + lines.joinToString("\n")
    }
}
