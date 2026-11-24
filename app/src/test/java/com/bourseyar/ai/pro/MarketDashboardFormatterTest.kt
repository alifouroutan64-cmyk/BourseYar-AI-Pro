package com.bourseyar.ai.pro

import org.junit.Assert.assertTrue
import org.junit.Test

class MarketDashboardFormatterTest {
    @Test
    fun formatsQuoteBookTradesAndClientFlow() {
        val snapshot = InstrumentLiveSnapshot(
            symbol = "فولاد",
            quote = LiveQuote(1250.0, 1200.0, 4.17, 50000.0, 120.0, "مجاز", null),
            orderBook = listOf(OrderBookLevel(1, 3.0, 900.0, 1240.0, 1260.0, 800.0, 2.0)),
            trades = listOf(RecentTrade("10:15:00", 1250.0, 100.0, 1.0)),
            clientFlow = ClientFlow(700.0, -200.0, 350.0, 100.0)
        )

        val text = MarketDashboardFormatter.format(snapshot, "10:16:00")

        assertTrue(text.contains("آخرین دریافت موفق برنامه: 10:16:00"))
        assertTrue(text.contains("نماد: فولاد"))
        assertTrue(text.contains("دفتر سفارش پنج‌سطحی"))
        assertTrue(text.contains("10:15:00 | قیمت 1250"))
        assertTrue(text.contains("جریان حقیقی/حقوقی"))
        assertTrue(text.contains("خالص حجم حقیقی: 700"))
    }

    @Test
    fun showsFallbacksWhenOptionalDataIsMissing() {
        val snapshot = InstrumentLiveSnapshot(
            symbol = "نماد",
            quote = LiveQuote(null, null, null, null, null, null, null),
            orderBook = emptyList(),
            trades = emptyList(),
            clientFlow = null
        )

        val text = MarketDashboardFormatter.format(snapshot, "—")

        assertTrue(text.contains("دفتر سفارش خالی است"))
        assertTrue(text.contains("معامله‌ای در پاسخ اخیر موجود نیست"))
        assertTrue(text.contains("اطلاعات جریان حقیقی/حقوقی موجود نیست"))
        assertTrue(text.contains("آخرین معامله: —"))
    }
}
