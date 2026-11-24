package com.bourseyar.ai.pro

import org.json.JSONObject

data class MarketWatchLoadResult(
    val renderedText: String?,
    val diagnosticReport: String,
    val failureMessage: String?
)

/** Coordinates market snapshot retrieval, JSON parsing and display formatting outside the Activity. */
object MarketWatchRepository {
    fun load(endpoint: String): MarketWatchLoadResult {
        var diagnostic = "گزارش خطای دریافت بازار\n"
        return try {
            val snapshot = MarketWatchDataSource.fetch(endpoint)
            val raw = snapshot.body
            diagnostic += "نشانی: $endpoint\nHTTP: ${snapshot.statusCode}\nنوع محتوا: ${snapshot.contentType}\nحجم پاسخ: ${raw.length} نویسه\n"
            require(snapshot.statusCode in 200..299) { "TSETMC پاسخ HTTP ${snapshot.statusCode} برگرداند" }

            val root = JSONObject(raw)
            val rows = root.optJSONArray("marketwatch")
                ?: root.optJSONArray("marketWatch")
                ?: error("آرایه marketwatch در پاسخ پیدا نشد؛ ساختار سرویس تغییر کرده است")
            diagnostic += "کلیدهای ریشه: ${root.keys().asSequence().toList().joinToString()}\nتعداد ردیف خام: ${rows.length()}\n"
            diagnostic += "نمونه سه ردیف اول:\n" +
                (0 until minOf(3, rows.length())).joinToString("\n---\n") { index ->
                    rows.optJSONObject(index)?.toString() ?: "ردیف $index شیء JSON نیست"
                }.take(3500) + "\n"

            val parsedResult = TsetmcMarketWatchParser.parse(rows)
            diagnostic += parsedResult.diagnostics
            require(parsedResult.rows.isNotEmpty()) {
                "پاسخ دریافت شد اما ردیف قیمت معتبر در آن نبود. ${parsedResult.diagnostics}"
            }
            MarketWatchLoadResult(
                MarketWatchFormatter.format(parsedResult.rows, 45),
                diagnostic,
                null
            )
        } catch (error: Exception) {
            diagnostic += "\nنوع استثنا: ${error.javaClass.name}\nشرح خطا: ${error.localizedMessage ?: "بدون توضیح"}\n"
            MarketWatchLoadResult(null, diagnostic, error.localizedMessage ?: "خطای شبکه یا پاسخ نامعتبر")
        }
    }
}
