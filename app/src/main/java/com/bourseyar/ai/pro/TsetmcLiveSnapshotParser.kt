package com.bourseyar.ai.pro

import org.json.JSONArray
import org.json.JSONObject

/** Parses the independent TSETMC live-dashboard responses without performing network requests. */
internal object TsetmcLiveSnapshotParser {
    private fun JSONObject.numberOrNull(vararg keys: String): Double? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = opt(key)
            val parsed = when (value) {
                is Number -> value.toDouble()
                is String -> value.replace(",", "").toDoubleOrNull()
                else -> null
            }
            if (parsed != null && parsed.isFinite() && parsed >= 0.0) return parsed
        }
        return null
    }

    fun parse(
        symbol: String,
        quoteBody: String,
        bookBody: String,
        tradesBody: String,
        flowBody: String?
    ): InstrumentLiveSnapshot {
        require(symbol.isNotBlank()) { "نام نماد خالی است" }
        val quoteRoot = JSONObject(quoteBody)
        val quote = quoteRoot.optJSONObject("closingPriceInfo")
            ?: error("اطلاعات قیمت نماد در پاسخ TSETMC وجود ندارد")
        val last = quote.numberOrNull("pDrCotVal", "last")
        val close = quote.numberOrNull("pClosing", "close")
        val change = quote.numberOrNull("priceChangePercent")
            ?: if (close != null && close > 0.0 && last != null) (last - close) / close * 100.0 else null
        val state = quote.optJSONObject("instrumentState")?.optString("cEtavalTitle")
            ?.takeIf { it.isNotBlank() && it != "null" }
        val time = quote.opt("hEven")?.toString()?.takeIf { it.isNotBlank() }
            ?: quote.opt("lastHEven")?.toString()?.takeIf { it.isNotBlank() }
        val liveQuote = LiveQuote(
            last, close, change,
            quote.numberOrNull("qTotTran5J", "tvol"),
            quote.numberOrNull("zTotTran", "tno"),
            state, time
        )

        val bookRoot = JSONObject(bookBody)
        val bookRows = bookRoot.optJSONArray("bestLimits") ?: JSONArray()
        val book = (0 until bookRows.length()).mapNotNull { index ->
            val row = bookRows.optJSONObject(index) ?: return@mapNotNull null
            val level = row.optInt("number", index + 1).coerceIn(1, 5)
            val bidPrice = row.numberOrNull("pd", "pMeDem")
            val bidVolume = row.numberOrNull("qd", "qTitMeDem")
            val bidCount = row.numberOrNull("zd", "zOrdMeDem")
            val askPrice = row.numberOrNull("po", "pMeOf")
            val askVolume = row.numberOrNull("qo", "qTitMeOf")
            val askCount = row.numberOrNull("zo", "zOrdMeOf")
            if (listOf(bidPrice, bidVolume, askPrice, askVolume).all { it == null }) null
            else OrderBookLevel(level, bidCount, bidVolume, bidPrice, askPrice, askVolume, askCount)
        }.sortedBy { it.level }

        val tradeRoot = JSONObject(tradesBody)
        val tradeRows = tradeRoot.optJSONArray("trade") ?: JSONArray()
        val trades = (0 until tradeRows.length()).mapNotNull { index ->
            val row = tradeRows.optJSONObject(index) ?: return@mapNotNull null
            val price = row.numberOrNull("pTran", "price")
            val volume = row.numberOrNull("qTitTran", "volume")
            if (price == null && volume == null) null
            else RecentTrade(
                row.opt("hEven")?.toString()?.takeIf { it.isNotBlank() } ?: "—",
                price, volume, row.numberOrNull("nTran", "count")
            )
        }.sortedByDescending { it.time }.take(20)

        val flow = flowBody?.let { body ->
            runCatching {
                val flowRoot = JSONObject(body)
                val row = flowRoot.optJSONObject("clientType") ?: return@runCatching null
                val buyI = row.numberOrNull("buy_I_Volume")
                val sellI = row.numberOrNull("sell_I_Volume")
                val buyN = row.numberOrNull("buy_N_Volume")
                val sellN = row.numberOrNull("sell_N_Volume")
                if (buyI == null || sellI == null || buyN == null || sellN == null) null
                else ClientFlow(
                    buyI - sellI, buyN - sellN,
                    row.numberOrNull("buy_CountI")?.takeIf { it > 0.0 }?.let { buyI / it },
                    row.numberOrNull("sell_CountI")?.takeIf { it > 0.0 }?.let { sellI / it }
                )
            }.getOrNull()
        }
        return InstrumentLiveSnapshot(symbol.trim().uppercase(), liveQuote, book, trades, flow)
    }
}
