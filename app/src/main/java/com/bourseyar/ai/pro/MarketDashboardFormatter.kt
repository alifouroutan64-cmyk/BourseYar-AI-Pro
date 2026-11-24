package com.bourseyar.ai.pro

import java.util.Locale

/** Formats live quote, order-book, trade and client-flow data for the Persian dashboard. */
object MarketDashboardFormatter {
    private fun Double?.display(pattern: String = "%.0f"): String =
        this?.let { pattern.format(Locale.US, it) } ?: "—"

    fun format(snapshot: InstrumentLiveSnapshot, receivedAt: String): String {
        val quote = snapshot.quote
        val quoteText = "نماد: ${snapshot.symbol} | وضعیت: ${quote.state ?: "نامشخص"}\n" +
            "آخرین معامله: ${quote.last.display()} | پایانی: ${quote.close.display()}\n" +
            "تغییر: ${quote.changePercent.display("%+.2f%%")} | حجم: ${quote.volume.display()} | تعداد معاملات: ${quote.tradeCount.display()}"

        val bookText = snapshot.orderBook.joinToString("\n") { level ->
            "سطح ${level.level} | خرید: ${level.bidPrice.display()} / حجم ${level.bidVolume.display()} / سفارش ${level.bidCount.display()}" +
                "   || فروش: ${level.askPrice.display()} / حجم ${level.askVolume.display()} / سفارش ${level.askCount.display()}"
        }.ifBlank { "دفتر سفارش خالی است یا بازار در حال معامله نیست." }

        val tradesText = snapshot.trades.joinToString("\n") { trade ->
            "${trade.time} | قیمت ${trade.price.display()} | حجم ${trade.volume.display()} | تعداد ${trade.count.display()}"
        }.ifBlank { "معامله‌ای در پاسخ اخیر موجود نیست." }

        val flow = snapshot.clientFlow
        val flowText = if (flow == null) "اطلاعات جریان حقیقی/حقوقی موجود نیست."
        else "خالص حجم حقیقی: ${flow.netIndividualVolume.display()} | خالص حجم حقوقی: ${flow.netLegalVolume.display()}\n" +
            "سرانه خرید حقیقی: ${flow.individualBuyPerBuyer.display()} | سرانه فروش حقیقی: ${flow.individualSellPerSeller.display()}"

        return "آخرین دریافت موفق برنامه: $receivedAt\n\n$quoteText\n\nدفتر سفارش پنج‌سطحی\n$bookText\n\n۲۰ معامله اخیر\n$tradesText\n\nجریان حقیقی/حقوقی\n$flowText"
    }
}
