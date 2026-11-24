package com.bourseyar.ai.pro

import org.json.JSONObject
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

    private fun resolveInstrumentCode(symbol: String): String? {
        val query = URLEncoder.encode(symbol.trim(), "UTF-8")
        val response = get("$BASE/Instrument/GetInstrumentSearch/$query")
        return TsetmcInstrumentResolver.resolve(symbol, response)
    }

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
        val code = resolveInstrumentCode(symbol) ?: return null
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
        val code = resolveInstrumentCode(symbol)
            ?: error("نماد دقیق «$symbol» پیدا نشد")

        val quoteBody = get("$BASE/ClosingPrice/GetClosingPriceInfo/$code")
        val bookBody = get("$BASE/BestLimits/$code")
        val tradesBody = get("$BASE/Trade/GetTrade/$code")
        val flowBody = runCatching {
            get("$BASE/ClientType/GetClientType/$code/1/0")
        }.getOrNull()

        return TsetmcLiveSnapshotParser.parse(
            symbol = symbol,
            quoteBody = quoteBody,
            bookBody = bookBody,
            tradesBody = tradesBody,
            flowBody = flowBody
        )
    }

    fun fetchDaily(symbol: String): HistoricalSeries {
        val code = resolveInstrumentCode(symbol)
            ?: error("نماد دقیق «$symbol» در جستجوی TSETMC پیدا نشد")
        val raw = get("$CHART?i=$code&t=ph&a=1")
        return TsetmcDailyHistoryParser.parse(symbol, raw)
    }
}
