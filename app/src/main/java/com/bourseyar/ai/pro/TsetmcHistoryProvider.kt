package com.bourseyar.ai.pro

import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
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

object TsetmcHistoryProvider {
    private const val BASE = "https://cdn.tsetmc.com/api"
    private const val CHART = "https://members.tsetmc.com/tsev2/chart/data/Financial.aspx"

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 20000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 BourseYarAIPro")
            setRequestProperty("Accept", "application/json,text/plain,*/*")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            require(code in 200..299) { "TSETMC پاسخ HTTP $code برگرداند" }
            require(body.isNotBlank()) { "پاسخ خالی از TSETMC" }
            return body
        } finally { connection.disconnect() }
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
            val volume = row.number("qTotTran5J", "tvol", "volume")
            val value = row.number("qTotCap", "tval", "tradedValue")
            if (code.isBlank() || symbol.isBlank() || volume <= 0 || value <= 0) null
            else MarketLeader(symbol, code, volume, value)
        }
        require(leaders.isNotEmpty()) {
            "دیده‌بان بازار ردیف معتبر برنگرداند؛ ممکن است سرویس محدود یا ساختار پاسخ تغییر کرده باشد"
        }
        return leaders.sortedByDescending { it.tradedValue }.take(limit)
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
        require(code.isNotBlank()) { "کد معاملاتی نماد دریافت نشد" }

        // a=1 requests backward-adjusted chart prices. Chart response columns:
        // date,pmax,pmin,pf,pl,tvol,pc
        val raw = get("$CHART?i=$code&t=ph&a=1")
        val candles = raw.split(";").mapNotNull { row ->
            val p = row.trim().split(",")
            if (p.size < 7) return@mapNotNull null
            val dateText = p[0].trim()
            if (!validChartDate(dateText)) return@mapNotNull null
            val high = p[1].toDoubleOrNull() ?: return@mapNotNull null
            val low = p[2].toDoubleOrNull() ?: return@mapNotNull null
            val open = p[3].toDoubleOrNull() ?: return@mapNotNull null
            val last = p[4].toDoubleOrNull() ?: return@mapNotNull null
            val volume = p[5].toDoubleOrNull() ?: return@mapNotNull null
            val close = p[6].toDoubleOrNull() ?: return@mapNotNull null
            if (listOf(high, low, open, last, volume, close).any { !it.isFinite() } ||
                low <= 0 || open <= 0 || close <= 0 || high < maxOf(open, low, close) ||
                low > minOf(open, close) || volume < 0) return@mapNotNull null
            dateText to Candle(close = close, high = high, low = low, volume = volume, open = open)
        }.sortedBy { it.first }
        require(candles.size >= 50) {
            "TSETMC برای $symbol فقط ${candles.size} کندل معتبر داد؛ حداقل ۵۰ لازم است"
        }
        require(candles.map { it.first }.distinct().size == candles.size) {
            "تاریخ تکراری در تاریخچه $symbol"
        }
        return HistoricalSeries(symbol.trim().uppercase(), candles.map { it.second })
    }
}
