package com.bourseyar.ai.pro

import org.json.JSONObject
import org.json.JSONArray
import java.net.URLEncoder
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Public, unofficial TSETMC endpoints. Validate every response; provider availability is not guaranteed. */
data class MarketLeader(val symbol: String, val instrumentCode: String, val volume: Double, val tradedValue: Double)
data class ClientFlow(
    val netIndividualVolume: Double?,
    val netLegalVolume: Double?,
    val individualBuyPerBuyer: Double?,
    val individualSellPerSeller: Double?
)

data class ClientFlowDay(
    val date: String,
    val netIndividualVolume: Double?,
    val netIndividualValue: Double?,
    val grossIndividualValue: Double?,
    val netLegalVolume: Double?,
    val individualBuyPerBuyer: Double?,
    val individualSellPerSeller: Double?
)


data class LiveQuote(
    val last: Double?, val close: Double?, val changePercent: Double?,
    val volume: Double?, val tradeCount: Double?, val state: String?, val time: String?
)
data class OrderBookLevel(
    val level: Int, val bidCount: Double?, val bidVolume: Double?, val bidPrice: Double?,
    val askPrice: Double?, val askVolume: Double?, val askCount: Double?
)
data class RecentTrade(val time: String, val price: Double?, val volume: Double?, val count: Double?)
data class InstrumentLiveSnapshot(
    val symbol: String, val quote: LiveQuote, val orderBook: List<OrderBookLevel>,
    val trades: List<RecentTrade>, val clientFlow: ClientFlow?
)

object TsetmcHistoryProvider {
    private const val BASE = "https://cdn.tsetmc.com/api"
    private const val CHART = "https://members.tsetmc.com/tsev2/chart/data/Financial.aspx"

    private fun get(url: String): String = TsetmcHttpClient.get(url)

    private fun validChartDate(value: String): Boolean {
        if (!value.matches(Regex("\\d{8}"))) return false
        val parser = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val pos = ParsePosition(0)
        return parser.parse(value, pos) != null && pos.index == value.length
    }

    private fun JSONObject.numberOrNull(vararg keys: String): Double? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = opt(key)
            val parsed = when (value) {
                is Number -> value.toDouble()
                is String -> value.replace(",", "").toDoubleOrNull()
                else -> null
            }
            if (parsed != null && parsed.isFinite() && parsed >= 0) return parsed
        }
        return null
    }


    private fun JSONObject.positiveNumber(vararg keys: String): Double? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = opt(key)
            val parsed = when (value) {
                is Number -> value.toDouble()
                is String -> value.replace(",", "").toDoubleOrNull()
                else -> null
            }
            if (parsed != null && parsed.isFinite() && parsed > 0.0) return parsed
        }
        return null
    }

    private fun JSONObject.number(vararg keys: String): Double {
        for (key in keys) {
            val value = opt(key)
            val parsed = when (value) {
                is Number -> value.toDouble()
                is String -> value.replace(",", "").toDoubleOrNull()
                else -> null
            }
            if (parsed != null && parsed.isFinite()) return parsed
        }
        return 0.0
    }

    /** Bulk حقیقی/حقوقی flow snapshot for the latest available trading session. */
    fun fetchClientFlowAll(): Map<String, ClientFlow> {
        val root = JSONObject(get("$BASE/ClientType/GetClientTypeAll"))
        val rows = root.optJSONArray("clientTypeAllDto")
            ?: error("ساختار جریان حقیقی/حقوقی TSETMC تغییر کرده است")
        val result = linkedMapOf<String, ClientFlow>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val code = row.optString("insCode")
            if (code.isBlank()) continue
            val buyI = row.numberOrNull("buy_I_Volume", "buyIVolume")
            val sellI = row.numberOrNull("sell_I_Volume", "sellIVolume")
            val buyN = row.numberOrNull("buy_N_Volume", "buyNVolume")
            val sellN = row.numberOrNull("sell_N_Volume", "sellNVolume")
            val buyCountI = row.numberOrNull("buy_CountI", "buy_I_Count", "buyCountI")
            val sellCountI = row.numberOrNull("sell_CountI", "sell_I_Count", "sellCountI")
            // Missing fields remain unknown; never silently convert them to zero.
            if (buyI == null || sellI == null || buyN == null || sellN == null) continue
            result[code] = ClientFlow(
                buyI - sellI,
                buyN - sellN,
                if (buyCountI != null && buyCountI > 0) buyI / buyCountI else null,
                if (sellCountI != null && sellCountI > 0) sellI / sellCountI else null
            )
        }
        require(result.isNotEmpty()) { "داده جریان حقیقی/حقوقی خالی است یا سرویس پاسخ معتبر نداد" }
        return result
    }

    /**
     * Historical daily individual/legal client activity for one instrument.
     * TSETMC returns recDate in Gregorian yyyyMMdd format.
     */
    fun fetchClientFlowHistory(instrumentCode: String): List<ClientFlowDay> {
        require(instrumentCode.matches(Regex("\\d{5,25}"))) { "کد معاملاتی نماد نامعتبر است" }
        val root = JSONObject(get("$BASE/ClientType/GetClientTypeHistory/$instrumentCode"))
        val rows = root.optJSONArray("clientType")
            ?: error("ساختار تاریخچه حقیقی/حقوقی TSETMC تغییر کرده است")
        val parsed = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val date = row.opt("recDate")?.toString().orEmpty()
            if (!date.matches(Regex("\\d{8}"))) return@mapNotNull null
            val buyI = row.numberOrNull("buy_I_Volume")
            val sellI = row.numberOrNull("sell_I_Volume")
            val buyN = row.numberOrNull("buy_N_Volume")
            val sellN = row.numberOrNull("sell_N_Volume")
            val buyIValue = row.numberOrNull("buy_I_Value")
            val sellIValue = row.numberOrNull("sell_I_Value")
            val buyCount = row.numberOrNull("buy_I_Count")
            val sellCount = row.numberOrNull("sell_I_Count")
            ClientFlowDay(
                date,
                if (buyI != null && sellI != null) buyI - sellI else null,
                if (buyIValue != null && sellIValue != null) buyIValue - sellIValue else null,
                if (buyIValue != null && sellIValue != null) buyIValue + sellIValue else null,
                if (buyN != null && sellN != null) buyN - sellN else null,
                if (buyI != null && buyCount != null && buyCount > 0) buyI / buyCount else null,
                if (sellI != null && sellCount != null && sellCount > 0) sellI / sellCount else null
            )
        }.distinctBy { it.date }.sortedBy { it.date }
        require(parsed.isNotEmpty()) { "تاریخچه جریان حقیقی/حقوقی برای این نماد خالی یا نامعتبر است" }
        return parsed
    }

    /** Fetch a single all-market snapshot and rank instruments by reported traded value. */
    fun fetchMarketLeaders(limit: Int = 20): List<MarketLeader> {
        require(limit in 1..100) { "تعداد نماد باید بین ۱ تا ۱۰۰ باشد" }
        val params = (0..8).joinToString("&") { "paperTypes%5B$it%5D=${it + 1}" }
        val url = "$BASE/ClosingPrice/GetMarketWatch?market=0&industrialGroup=&$params&showTraded=false&withBestLimits=false&hEven=0&RefID=0"
        val root = JSONObject(get(url))
        val rows = root.optJSONArray("marketwatch")
            ?: error("ساختار دیده‌بان بازار TSETMC تغییر کرده یا پاسخ مسدود شده است")
        val leaders = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val code = row.optString("insCode")
            val symbol = sequenceOf("lva", "lVal18AFC", "symbol").map { row.optString(it) }
                .firstOrNull { it.isNotBlank() && it != "null" }.orEmpty()
            // Keep the scanner universe to common shares; derivatives/options and debt instruments are excluded.
            val instrumentType = row.numberOrNull("yVal", "yval")?.toInt()
            if (instrumentType != 300) return@mapNotNull null
            // Compact MarketWatch aliases may be populated while descriptive fields are zero.
            val volume = row.positiveNumber("qTotTran5J", "tvol", "volume", "qtj")
            val value = row.positiveNumber("qTotCap", "tval", "tradedValue", "qtc")
            if (code.isBlank() || symbol.isBlank() || volume == null || value == null) null
            else MarketLeader(symbol, code, volume, value)
        }
        require(leaders.isNotEmpty()) {
            "دیده‌بان بازار ردیف معتبر برنگرداند؛ ممکن است سرویس محدود یا ساختار پاسخ تغییر کرده باشد"
        }
        return leaders.sortedByDescending { it.tradedValue }.take(limit)
    }


    /** Fetch a conservative five-session money-flow score for a symbol; null means unavailable. */
    fun fetchFiveSessionFlowScore(symbol: String): Int? {
        val query = URLEncoder.encode(symbol.trim(), "UTF-8")
        val search = JSONObject(get("$BASE/Instrument/GetInstrumentSearch/$query"))
        val items = search.optJSONArray("instrumentSearch")
            ?: error("ساختار جستجوی نماد برای جریان پول تغییر کرده است")
        val normalized = symbol.trim().replace("ي", "ی").replace("ك", "ک")
        val match = (0 until items.length()).map { items.getJSONObject(it) }
            .firstOrNull {
                it.optString("lVal18AFC").replace("ي", "ی").replace("ك", "ک") == normalized
            } ?: return null
        val code = match.optString("insCode")
        if (code.isBlank()) return null
        val days = fetchClientFlowHistory(code).takeLast(5)
        val valid = days.mapNotNull { day ->
            val net = day.netIndividualValue
            val gross = day.grossIndividualValue
            if (net == null || gross == null || gross <= 0.0) null else day to (net to gross)
        }
        if (valid.isEmpty()) return null
        val netTotal = valid.sumOf { it.second.first }
        val grossTotal = valid.sumOf { it.second.second }
        val positive = valid.count { it.second.first > 0.0 }
        return AnalysisEngine.normalizedFlowScore(netTotal, grossTotal, positive, valid.size)
    }


    /** One-symbol live dashboard: quote, five-level book, recent trades and client flow. */
    fun fetchLiveSnapshot(symbol: String): InstrumentLiveSnapshot {
        val query = URLEncoder.encode(symbol.trim(), "UTF-8")
        val search = JSONObject(get("$BASE/Instrument/GetInstrumentSearch/$query"))
        val items = search.optJSONArray("instrumentSearch")
            ?: error("ساختار جستجوی نماد TSETMC تغییر کرده است")
        val normalized = symbol.trim().replace("ي", "ی").replace("ك", "ک")
        val match = (0 until items.length()).map { items.getJSONObject(it) }
            .firstOrNull { it.optString("lVal18AFC").replace("ي", "ی").replace("ك", "ک") == normalized }
            ?: error("نماد دقیق «$symbol» پیدا نشد")
        val code = match.optString("insCode")
        require(code.matches(Regex("\\d{5,25}"))) { "کد معاملاتی نماد نامعتبر است" }

        val quoteRoot = JSONObject(get("$BASE/ClosingPrice/GetClosingPriceInfo/$code"))
        val quote = quoteRoot.optJSONObject("closingPriceInfo")
            ?: error("اطلاعات قیمت نماد در پاسخ TSETMC وجود ندارد")
        val last = quote.numberOrNull("pDrCotVal", "last")
        val close = quote.numberOrNull("pClosing", "close")
        val change = quote.numberOrNull("priceChangePercent")
            ?: if (close != null && close > 0 && last != null) (last - close) / close * 100.0 else null
        val state = quote.optJSONObject("instrumentState")?.optString("cEtavalTitle")?.takeIf { it.isNotBlank() && it != "null" }
        val time = quote.opt("hEven")?.toString()?.takeIf { it.isNotBlank() }
            ?: quote.opt("lastHEven")?.toString()?.takeIf { it.isNotBlank() }
        val liveQuote = LiveQuote(
            last, close, change,
            quote.numberOrNull("qTotTran5J", "tvol"),
            quote.numberOrNull("zTotTran", "tno"),
            state, time
        )

        val bookRoot = JSONObject(get("$BASE/BestLimits/$code"))
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

        val tradeRoot = JSONObject(get("$BASE/Trade/GetTrade/$code"))
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

        val flow = runCatching {
            val flowRoot = JSONObject(get("$BASE/ClientType/GetClientType/$code/1/0"))
            val row = flowRoot.optJSONObject("clientType") ?: return@runCatching null
            val buyI = row.numberOrNull("buy_I_Volume")
            val sellI = row.numberOrNull("sell_I_Volume")
            val buyN = row.numberOrNull("buy_N_Volume")
            val sellN = row.numberOrNull("sell_N_Volume")
            if (buyI == null || sellI == null || buyN == null || sellN == null) null
            else ClientFlow(
                buyI - sellI, buyN - sellN,
                row.numberOrNull("buy_CountI")?.takeIf { it > 0 }?.let { buyI / it },
                row.numberOrNull("sell_CountI")?.takeIf { it > 0 }?.let { sellI / it }
            )
        }.getOrNull()
        return InstrumentLiveSnapshot(symbol.trim().uppercase(), liveQuote, book, trades, flow)
    }

    fun fetchDaily(symbol: String): HistoricalSeries {
        val query = URLEncoder.encode(symbol.trim(), "UTF-8")
        val search = JSONObject(get("$BASE/Instrument/GetInstrumentSearch/$query"))
        val items = search.optJSONArray("instrumentSearch")
            ?: error("ساختار پاسخ جستجوی نماد تغییر کرده است")
        val normalized = symbol.trim().replace("ي", "ی").replace("ك", "ک")
        val match = (0 until items.length()).map { items.getJSONObject(it) }
            .firstOrNull {
                it.optString("lVal18AFC").replace("ي", "ی").replace("ك", "ک") == normalized
            } ?: error("نماد دقیق «$symbol» در جستجوی TSETMC پیدا نشد")
        val code = match.optString("insCode")
        require(code.matches(Regex("\\d{5,25}"))) { "کد معاملاتی نماد نامعتبر است" }
        val raw = get("$CHART?i=$code&t=ph&a=1")
        return TsetmcDailyHistoryParser.parse(symbol, raw)
    }
}
