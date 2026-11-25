package com.bourseyar.ai.pro

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Free Codal search API. Its schema is not a stable contract; this parser is intentionally defensive. */
object CodalPublicApi {
    private const val BASE = "https://search.codal.ir/api/search/v2/q"

    private class CodalHttpException(val statusCode: Int) : IOException("کدال HTTP $statusCode")
    private val requestLock = Any()
    @Volatile private var lastRequestAtMillis = 0L
    @Volatile private var blockedUntilMillis = 0L

    private fun waitForRequestSlot() {
        synchronized(requestLock) {
            val now = System.currentTimeMillis()
            val cooldownMillis = blockedUntilMillis - now
            if (cooldownMillis > 0) {
                throw IOException("کدال به‌دلیل محدودیت درخواست موقتاً متوقف شده است؛ حدود ${(cooldownMillis + 999) / 1000} ثانیه دیگر تلاش کنید")
            }
            val waitMillis = 2_000L - (now - lastRequestAtMillis)
            if (waitMillis > 0) Thread.sleep(waitMillis)
            lastRequestAtMillis = System.currentTimeMillis()
        }
    }

    private fun activateRateLimitCooldown() {
        synchronized(requestLock) {
            // A 429 can indicate an IP-wide block. Avoid hammering Codal from other screens.
            blockedUntilMillis = maxOf(blockedUntilMillis, System.currentTimeMillis() + 60_000L)
        }
    }

    private fun request(url: String): String {
        waitForRequestSlot()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) BourseYarAIPro")
            setRequestProperty("Accept", "application/json,text/html,application/vnd.ms-excel,*/*")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw CodalHttpException(code)
            require(body.isNotBlank()) { "پاسخ خالی کدال" }
            return body
        } finally { conn.disconnect() }
    }

    /**
     * Search once, then fetch at most one recent financial-statement workbook.
     * Excel downloads may be blocked by geography/rate limits or use legacy binary
     * formats; those cases stay explicitly unavailable instead of becoming zeroes.
     */
    fun searchFilingsWithMetrics(symbol: String, limit: Int = 20): FundamentalSnapshot {
        val snapshot = searchFilings(symbol, limit)
        val statement = snapshot.recentFilings.firstOrNull {
            it.category == "صورت‌های مالی" && it.excelUrl != null
        } ?: return snapshot.copy(sourceNote = "اطلاعیه‌ها دریافت شد؛ فایل اکسل صورت مالی در پنج اطلاعیه اخیر موجود نبود")

        val extracted = runCatching {
            val safeUrl = safeCodalExcelUrl(statement.excelUrl)
                ?: throw IOException("پیوند اکسل رسمی معتبر نیست")
            val body = try {
                request(safeUrl)
            } catch (e: CodalHttpException) {
                if (e.statusCode == 429) {
                    activateRateLimitCooldown()
                    throw IOException("کدال پاسخ ۴۲۹ داده است؛ درخواست‌های بعدی تا ۶۰ ثانیه متوقف شدند", e)
                }
                throw e
            }
            if (!body.contains("<table", ignoreCase = true)) {
                throw IOException("فایل اکسل در قالب HTML قابل‌خواندن نبود؛ احتمالاً فایل قدیمی یا پاسخ محدودشده کدال است")
            }
            CodalFinancialParser.parseHtml(body)
        }.getOrElse { error ->
            return snapshot.copy(sourceNote = "اطلاعیه‌ها دریافت شد؛ استخراج اکسل ناموفق: ${error.message ?: "خطای نامشخص"}")
        }

        if (!extracted.hasMetrics) {
            return snapshot.copy(sourceNote = "فایل اکسل دریافت شد، اما ردیف‌های مالی قابل‌تشخیص نبودند؛ شاخصی حدس زده نشد")
        }
        return snapshot.copy(
            revenueGrowthPercent = extracted.revenueGrowthPercent,
            netProfitGrowthPercent = extracted.netProfitGrowthPercent,
            roePercent = extracted.returnOnEquityPercent,
            debtToEquity = extracted.debtToEquity,
            revenue = extracted.totalRevenue,
            netProfit = extracted.netProfit,
            totalAssets = extracted.totalAssets,
            totalEquity = extracted.totalEquity,
            totalLiabilities = extracted.totalLiabilities,
            sourceNote = "استخراج آزمایشی از برگه ۰ اکسل رسمی کدال؛ فقط ردیف‌های دقیقاً شناسایی‌شده نمایش داده می‌شوند؛ وضعیت تلفیقی/جداگانه و واحد ارقام نیازمند بررسی گزارش است"
        )
    }

    fun searchFilings(symbol: String, limit: Int = 20): FundamentalSnapshot {
        val urls = CodalSearchRequest.urls(symbol, limit)
        var lastError: Exception? = null
        for (url in urls) {
            try {
                val parsed = CodalFilingSearchParser.parse(request(url), symbol)
                if (parsed != null) return parsed
            } catch (e: Exception) {
                lastError = e
                // A 429 means Codal is throttling this client. Do not immediately hit
                // the alternate query shape; that only increases the block window.
                if (e is CodalHttpException && e.statusCode == 429) {
                    activateRateLimitCooldown()
                    throw IOException("کدال پاسخ ۴۲۹ داده است؛ برای جلوگیری از مسدودشدن بیشتر، درخواست‌های جدید تا ۶۰ ثانیه متوقف شدند", e)
                }
            }
        }
        throw lastError ?: IllegalStateException("ساختار جستجوی کدال شناخته نشد")
    }
}
