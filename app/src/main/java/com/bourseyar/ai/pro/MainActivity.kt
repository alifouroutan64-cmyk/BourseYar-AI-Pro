package com.bourseyar.ai.pro

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val bg = Color.rgb(13, 24, 38)
    private val panel = Color.rgb(24, 40, 58)
    private val accent = Color.rgb(255, 202, 92)
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("bourseyar", MODE_PRIVATE) }
    private val endpoint = "https://cdn.tsetmc.com/api/ClosingPrice/GetMarketWatch?market=0&priceType=1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg; window.navigationBarColor = bg
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 20, 18, 12); setBackgroundColor(bg) }
        val title = label("بورس‌یار AI Pro", 25f, Color.WHITE, true)
        title.gravity = Gravity.CENTER
        status = label("نسخه توسعه‌ای | داده بازار هنوز اعتبارسنجی نشده", 12f, accent)
        status.gravity = Gravity.CENTER
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("بازار", "تحلیل", "اسکنر", "سبد", "راهنما").forEach { name ->
            nav.addView(Button(this).apply { text = name; textSize = 11f; setOnClickListener { show(name) } },
                LinearLayout.LayoutParams(0, -2, 1f))
        }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(body) }
        root.addView(title); root.addView(status); root.addView(nav)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        show("بازار")
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply { text = value; textSize = size; setTextColor(color); if (bold) setTypeface(null, 1); setPadding(8, 8, 8, 8) }

    private fun card(title: String, text: String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 10, 12, 10); setBackgroundColor(panel) }
        box.addView(label(title, 17f, accent, true))
        box.addView(label(text, 14f, Color.WHITE))
        val lp = LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 8, 0, 8); body.addView(box, lp)
    }

    private fun show(page: String) {
        body.removeAllViews()
        when (page) {
            "بازار" -> {
                card("نمای کلی بازار", "برای دریافت پاسخ خام از سرویس عمومی TSETMC دکمه زیر را بزنید. تا زمان شناخت و اعتبارسنجی ساختار پاسخ، قیمت‌ها و نمادها به‌عنوان داده معاملاتی نمایش داده نمی‌شوند.")
                body.addView(Button(this).apply { text = "دریافت داده بازار"; setOnClickListener { loadMarket() } })
                card("وضعیت منبع", "منبع فعلی: endpoint عمومی و غیررسمی TSETMC\nتضمین پایداری یا دسترس‌پذیری ندارد. داده خام، سیگنال خرید نیست.")
            }
            "تحلیل" -> {
                card("تحلیل تکنیکال", "محاسبه‌گر RSI(14)، EMA20/50، MACD، ATR و حمایت/مقاومت برای سری کندل‌های معتبر در موتور تحلیل اضافه شده است. تا زمان اتصال منبع تاریخچه OHLC معتبر، تحلیل نماد واقعی ارائه نمی‌شود.")
                val prices = EditText(this).apply { hint = "قیمت‌های پایانی با کاما جدا شوند؛ مثال: 100,102,101"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY) }
                val highs = EditText(this).apply { hint = "اختیاری: سقف‌ها با کاما"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY) }
                val lows = EditText(this).apply { hint = "اختیاری: کف‌ها با کاما"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY) }
                body.addView(prices); body.addView(highs); body.addView(lows)
                body.addView(Button(this).apply {
                    text = "محاسبه شاخص‌ها"
                    setOnClickListener {
                        try {
                            val close = prices.text.toString().split(",").map { it.trim().toDouble() }
                            val hi = highs.text.toString().takeIf { it.isNotBlank() }?.split(",")?.map { it.trim().toDouble() }
                            val lo = lows.text.toString().takeIf { it.isNotBlank() }?.split(",")?.map { it.trim().toDouble() }
                            require(close.size >= 20) { "حداقل ۲۰ قیمت پایانی وارد کنید" }
                            require(hi == null || hi.size == close.size) { "تعداد سقف‌ها باید با قیمت‌ها برابر باشد" }
                            require(lo == null || lo.size == close.size) { "تعداد کف‌ها باید با قیمت‌ها برابر باشد" }
                            val candles = close.indices.map { i -> Candle(close[i], hi?.get(i) ?: close[i], lo?.get(i) ?: close[i]) }
                            val r = AnalysisEngine.analyze(candles) ?: error("داده کافی نیست")
                            card("نتیجه محاسبه (داده ورودی شما)", "آخرین قیمت: ${r.last}\nروند: ${r.trend}\nامتیاز قواعدی: ${r.score}/100\nRSI: ${r.rsi ?: "—"}\nEMA20: ${r.ema20 ?: "—"}\nEMA50: ${r.ema50 ?: "—"}\nMACD: ${r.macd ?: "—"}\nATR: ${r.atr ?: "—"}\nحمایت تقریبی: ${r.support}\nمقاومت تقریبی: ${r.resistance}\n\n" + r.notes.joinToString("\n"))
                        } catch (e: Exception) { card("ورودی نامعتبر", e.message ?: "قیمت‌ها را بررسی کنید") }
                    }
                })
                card("هشدار", "امتیاز الگوریتمی احتمال موفقیت را تضمین نمی‌کند و توصیه خرید/فروش نیست. برای سیگنال معتبر، تاریخچه تعدیل‌شده و داده حجم/معاملات لازم است.")
            }
            "اسکنر" -> {
                card("اسکنر تاریخی OHLCV", "CSV را با ستون‌های symbol,date,open,high,low,close,volume وارد کنید. تاریخ yyyy-MM-dd و حداقل ۵۰ کندل روزانه برای هر نماد لازم است. ردیف‌ها بر اساس تاریخ مرتب می‌شوند.")
                val input = EditText(this).apply {
                    hint = "symbol,date,open,high,low,close,volume\nفولاد,2026-01-05,2500,2540,2480,2520,1250000"
                    setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
                    minLines = 8; gravity = Gravity.TOP
                }
                body.addView(input)
                val symbolsInput = EditText(this).apply {
                    hint = "نمادها برای دریافت خودکار: فولاد، فملی، شپنا"
                    setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
                }
                body.addView(symbolsInput)
                lateinit var leadersButton: Button
                leadersButton = Button(this).apply {
                    text = "یافتن ۲۰ نماد برتر بازار بر اساس ارزش معاملات"
                    setOnClickListener {
                        isEnabled = false
                        text = "در حال دریافت دیده‌بان بازار…"
                        Thread {
                            try {
                                val leaders = TsetmcHistoryProvider.fetchMarketLeaders(20)
                                val flows = runCatching { TsetmcHistoryProvider.fetchClientFlowAll() }.getOrDefault(emptyMap())
                                // Limit historical requests to five candidates to avoid hammering the public endpoint.
                                val historicalFlows = leaders.take(5).associate { item ->
                                    item.instrumentCode to runCatching {
                                        TsetmcHistoryProvider.fetchClientFlowHistory(item.instrumentCode).takeLast(5)
                                    }
                                }
                                runOnUiThread {
                                    symbolsInput.setText(leaders.joinToString("، ") { it.symbol })
                                    val flowRanked = leaders.take(5).mapNotNull { item ->
                                        val history = historicalFlows[item.instrumentCode]?.getOrNull() ?: return@mapNotNull null
                                        val valid = history.filter { it.netIndividualValue != null && it.grossIndividualValue != null && it.grossIndividualValue > 0 }
                                        if (valid.isEmpty()) return@mapNotNull null
                                        val gross = valid.sumOf { it.grossIndividualValue!! }
                                        if (gross <= 0) return@mapNotNull null
                                        val net = valid.sumOf { it.netIndividualValue!! }
                                        val pressurePct = 100.0 * net / gross
                                        val positiveDays = valid.count { it.netIndividualValue!! > 0 }
                                        val flowScore = AnalysisEngine.normalizedFlowScore(net, gross, positiveDays, valid.size)
                                            ?: return@mapNotNull null
                                        Triple(item, flowScore, Triple(pressurePct, positiveDays, valid.size))
                                    }.sortedByDescending { it.second }
                                    val flowText = flowRanked.joinToString("\n") { (item, score, stats) ->
                                        val flow = flows[item.instrumentCode]
                                        val netVolume = flow?.netIndividualVolume?.let { "%.0f".format(java.util.Locale.US, it) } ?: "ناموجود"
                                        val perBuyer = flow?.individualBuyPerBuyer?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"
                                        "امتیاز جریان: $score/100 | ${item.symbol} | فشار خالص ارزش حقیقی: ${"%.1f".format(java.util.Locale.US, stats.first)}٪ | روز مثبت: ${stats.second}/${stats.third} | خالص حجم آخرین جلسه: $netVolume | سرانه خرید: $perBuyer | ارزش معاملات: ${"%.0f".format(java.util.Locale.US, item.tradedValue)}"
                                    }
                                    card("رتبه‌بندی جریان پول حقیقی در ۵ نماد پرتراکنش", flowText.ifBlank { "داده معتبر ارزش حقیقی/حقوقی برای محاسبه فشار پول دریافت نشد؛ رتبه‌بندی انجام نشد." } + "\nامتیاز جریان = ۷۰٪ فشار خالص ارزش خرید حقیقی به کل ارزش خریدوفروش حقیقی + ۳۰٪ نسبت جلسات مثبت. فقط روزهای دارای هر دو مقدار معتبر محاسبه می‌شوند. این امتیاز غربالگری است، نه سیگنال خرید.")
                                    card("دیده‌بان ارزش معاملات", leaders.take(10).joinToString("\n") { item ->
                                        "${item.symbol} | حجم ${"%.0f".format(java.util.Locale.US, item.volume)} | ارزش گزارش‌شده ${"%.0f".format(java.util.Locale.US, item.tradedValue)}"
                                    } + "\n۲۰ نماد اول بر اساس ارزش گزارش‌شده در فهرست ورودی اسکن قرار گرفتند.")
                                    leadersButton.isEnabled = true
                                    leadersButton.text = "به‌روزرسانی ۲۰ نماد برتر بازار"
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    card("خطا در دیده‌بان بازار", e.message ?: "خطای شبکه یا ساختار پاسخ")
                                    leadersButton.isEnabled = true
                                    leadersButton.text = "تلاش مجدد برای دریافت نمادهای برتر"
                                }
                            }
                        }.start()
                    }
                }
                body.addView(leadersButton)
                val minVolumeInput = EditText(this).apply {
                    hint = "حداقل میانگین حجم روزانه ۲۰ روز (۰ = غیرفعال)"
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    setText("0")
                    setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
                }
                body.addView(minVolumeInput)
                val scoreInput = EditText(this).apply {
                    hint = "حداقل امتیاز سیگنال برای بک‌تست (۰ تا ۱۰۰)"
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    setText("70")
                    setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
                }
                body.addView(scoreInput)
                val feeInput = EditText(this).apply {
                    hint = "هزینه رفت‌وبرگشت به واحد basis point (۰ = بدون کارمزد)"
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setText("0")
                    setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
                }
                body.addView(feeInput)
                lateinit var autoScanButton: Button
                autoScanButton = Button(this).apply {
                    text = "دریافت تاریخچه از TSETMC و اسکن"
                    setOnClickListener {
                        val symbols = symbolsInput.text.toString().split(",", "،")
                            .map { it.trim() }.filter { it.isNotBlank() }.distinct()
                        val minAvgVol = minVolumeInput.text.toString().toDoubleOrNull()
                        val minScore = scoreInput.text.toString().toIntOrNull()
                        val feeBps = feeInput.text.toString().toDoubleOrNull()
                        if (symbols.isEmpty()) {
                            card("ورودی لازم است", "یک یا چند نماد را با ویرگول جدا کنید")
                        } else if (minAvgVol == null || !minAvgVol.isFinite() || minAvgVol < 0) {
                            card("فیلتر حجم نامعتبر", "حداقل حجم را به صورت عدد صحیح نامنفی وارد کنید")
                        } else if (minScore == null || minScore !in 0..100 || feeBps == null || !feeBps.isFinite() || feeBps < 0) {
                            card("تنظیم بک‌تست نامعتبر", "امتیاز باید بین ۰ تا ۱۰۰ و هزینه رفت‌وبرگشت عدد نامنفی باشد")
                        } else {
                            autoScanButton.isEnabled = false
                            symbolsInput.isEnabled = false
                            minVolumeInput.isEnabled = false
                            scoreInput.isEnabled = false
                            feeInput.isEnabled = false
                            autoScanButton.text = "در حال دریافت تاریخچه…"
                            Thread {
                                try {
                                    val attempts = symbols.map { symbol -> symbol to runCatching { TsetmcHistoryProvider.fetchDaily(symbol) } }
                                    val series = attempts.mapNotNull { it.second.getOrNull() }
                                    require(series.isNotEmpty()) { attempts.joinToString("؛ ") { (symbol, result) -> "$symbol: ${result.exceptionOrNull()?.message ?: "دریافت نشد"}" } }
                                    val flowScores = attempts.mapNotNull { (symbol, result) ->
                                        result.getOrNull()?.let { data ->
                                            symbol to runCatching { TsetmcHistoryProvider.fetchFiveSessionFlowScore(symbol) }.getOrNull()
                                        }
                                    }.toMap()
                                    val results = series.mapNotNull { data ->
                                        val candidate = AnalysisEngine.scanCandles(data.symbol, data.candles, minAvgVol) ?: return@mapNotNull null
                                        val previousVolumes = data.candles.dropLast(1).takeLast(20).map { it.volume }
                                        val averageVolume = previousVolumes.average()
                                        val relativeVolume = if (averageVolume > 0.0) data.candles.last().volume / averageVolume else null
                                        val flowScore = flowScores[data.symbol]
                                        val stopPrice = candidate.report.last * (1.0 - candidate.riskPercent / 100.0)
                                        val riskAmount = candidate.report.last - stopPrice
                                        val rewardRisk = if (riskAmount > 0.0) (candidate.target1 - candidate.report.last) / riskAmount else null
                                        val composite = OpportunityScoring.score(candidate.report.score, flowScore, relativeVolume, rewardRisk)
                                        Triple(candidate, composite, Pair(relativeVolume, flowScore))
                                    }.sortedByDescending { it.second ?: -1 }
                                    val backtests = series.map { it.symbol to AnalysisEngine.backtest(it.candles, minScore, feeBps) }
                                    val fetchErrors = attempts.filter { it.second.isFailure }
                                    runOnUiThread {
                                        card("نتیجه اسکن خودکار TSETMC | ${results.size} نماد",
                                            results.joinToString("\n\n") { row ->
                                                val x = row.first
                                                "${x.symbol} | امتیاز تکنیکال ${x.report.score}/100 | امتیاز ترکیبی ${row.second ?: "—"}/100 | روند ${x.report.trend}\n" +
                                                "حجم نسبی: ${row.third.first?.let { "%.2fx".format(java.util.Locale.US, it) } ?: "ناموجود"} | امتیاز جریان پول ۵ جلسه: ${row.third.second ?: "ناموجود"}\n" +
                                                "آخرین قیمت تعدیل‌شده: ${x.report.last}\nRSI: ${x.report.rsi ?: "—"} | ATR: ${x.report.atr ?: "—"}\n" +
                                                "حدضرر آزمایشی: ${x.report.last * (1.0 - x.riskPercent / 100.0)}\n" +
                                                "هدف ۱.۵R: ${x.target1} | هدف ۲R: ${x.target2}\n" +
                                                "ریسک فاصله حدضرر: %.2f%%".format(java.util.Locale.US, x.riskPercent) +
                                                "\n" + x.report.notes.joinToString("؛ ")
                                            })
                                        card("فیلتر نقدشوندگی تقریبی", "حداقل میانگین حجم ۲۰ روز قبل: %.0f سهم. این فیلتر فقط تعداد سهم معامله‌شده را می‌سنجد؛ ارزش ریالی معاملات، سهام شناور، عمق سفارش‌ها، توقف نماد و امکان اجرای سفارش را تأیید نمی‌کند.".format(java.util.Locale.US, minAvgVol))
                                        fetchErrors.forEach { (symbol, result) -> card("خطای دریافت $symbol", result.exceptionOrNull()?.message ?: "نامشخص") }
                                        backtests.forEach { (symbol, rows) ->
                                            card("بک‌تست $symbol | آستانه امتیاز $minScore", rows.joinToString("\n") { b ->
                                                "نگهداری ${b.holdingDays} روز: ${b.trades} معامله | برد %.1f%% | میانگین بازده %.2f%% | PF %.2f | افت سرمایه %.2f%%".format(java.util.Locale.US, b.winRatePercent, b.averageReturnPercent, b.profitFactor, b.maxDrawdownPercent)
                                            } + "\nهزینه رفت‌وبرگشت اعمال‌شده: $feeBps bps. معاملات هم‌پوشان‌اند و نتیجه تضمین عملکرد آینده نیست.")
                                        }
                                        card("منبع و محدودیت", "تاریخچه از API عمومی و غیررسمی TSETMC دریافت شده و قیمت‌های نموداری با گزینه تعدیل‌شده درخواست شده‌اند. این اتصال ممکن است محدود یا تغییر کند؛ نمادهای ناموجود/تاریخچه ناقص با خطا رد می‌شوند. هنوز اسکن کل بازار و راستی‌آزمایی لحظه‌ای وضعیت نماد انجام نمی‌شود.")
                                        symbolsInput.isEnabled = true
                                        minVolumeInput.isEnabled = true
                                        scoreInput.isEnabled = true
                                        feeInput.isEnabled = true
                                        autoScanButton.isEnabled = true
                                        autoScanButton.text = "دریافت تاریخچه از TSETMC و اسکن"
                                    }
                                } catch (e: Exception) {
                                    runOnUiThread {
                                        card("خطا در دریافت TSETMC", e.message ?: "خطای شبکه یا ساختار پاسخ")
                                        symbolsInput.isEnabled = true
                                        minVolumeInput.isEnabled = true
                                        scoreInput.isEnabled = true
                                        feeInput.isEnabled = true
                                        autoScanButton.isEnabled = true
                                        autoScanButton.text = "تلاش مجدد برای دریافت از TSETMC"
                                    }
                                }
                            }.start()
                        }
                    }
                }
                body.addView(autoScanButton)
                body.addView(Button(this).apply {
                    text = "اعتبارسنجی و اسکن CSV"
                    setOnClickListener {
                        try {
                            val series = MarketCsvParser.parse(input.text.toString())
                            val minAvgVol = minVolumeInput.text.toString().toDoubleOrNull()
                                ?.takeIf { it.isFinite() && it >= 0 } ?: error("حداقل میانگین حجم نامعتبر است")
                            val minScore = scoreInput.text.toString().toIntOrNull()?.takeIf { it in 0..100 }
                                ?: error("حداقل امتیاز باید بین ۰ تا ۱۰۰ باشد")
                            val feeBps = feeInput.text.toString().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
                                ?: error("هزینه رفت‌وبرگشت نامعتبر است")
                            val results = series.mapNotNull { AnalysisEngine.scanCandles(it.symbol, it.candles, minAvgVol) }
                                .sortedByDescending { it.report.score }
                            require(results.isNotEmpty()) { "هیچ سری معتبر برای اسکن پیدا نشد" }
                            card("نتیجه اسکن | ${results.size} نماد", results.joinToString("\n\n") { x ->
                                "${x.symbol} | امتیاز ${x.report.score}/100 | روند ${x.report.trend}\n" +
                                "آخرین قیمت: ${x.report.last}\nRSI: ${x.report.rsi ?: "—"} | ATR: ${x.report.atr ?: "—"}\n" +
                                "حدضرر آزمایشی ATR: ${x.report.last * (1.0 - x.riskPercent / 100.0)}\n" +
                                "هدف ۱.۵R: ${x.target1} | هدف ۲R: ${x.target2}\n" +
                                "ریسک فاصله حدضرر: %.2f%%".format(java.util.Locale.US, x.riskPercent) +
                                "\n" + x.report.notes.joinToString("؛ ")
                            })
                            series.forEach { item ->
                                val bt = AnalysisEngine.backtest(item.candles, minScore, feeBps)
                                card("بک‌تست ${item.symbol}", bt.joinToString("\n") { b ->
                                    "${b.holdingDays} روز | ${b.trades} معامله | برد %.1f%% | میانگین %.2f%% | PF %.2f | افت سرمایه %.2f%%".format(java.util.Locale.US, b.winRatePercent, b.averageReturnPercent, b.profitFactor, b.maxDrawdownPercent)
                                } + "\nکارمزد رفت‌وبرگشت: $feeBps bps. ورود در بازشدن روز بعد، خروج با حدضرر/هدف یا پایان افق؛ معاملات هم‌پوشان‌اند.")
                            }
                            card("محدودیت مهم", "بک‌تست فقط یک شبیه‌سازی ساده تاریخی است؛ معاملات هم‌پوشان‌اند و افت سرمایه بر اساس توالی معاملات است، نه پرتفوی هم‌زمان. صف، لغزش قیمت و محدودیت دامنه نوسان لحاظ نشده و کارمزد فقط به‌صورت ورودی کاربر اعمال می‌شود. CSV باید از منبع معتبر و با قیمت‌های هم‌مقیاس تهیه شود؛ نتیجه توصیه قطعی خرید نیست.")
                        } catch (e: Exception) { card("خطا در CSV", e.message ?: "ساختار و داده‌ها را بررسی کنید") }
                    }
                })
                card("ساختار CSV", "هر خط: نماد،تاریخ،بازشدن،بیشینه،کمینه،پایانی،حجم. اعداد لاتین و تاریخ میلادی لازم است. برای هر نماد حداقل ۵۰ تاریخ یکتا وارد کنید.")
            }
            "سبد" -> {
                card("مدیریت سبد آزمایشی", "اطلاعات این بخش روی همین گوشی ذخیره می‌شود؛ قبل از اتکا، کارمزد، مالیات و جزئیات معاملات را تطبیق دهید.")
                val symbol = EditText(this).apply { hint = "نماد"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY) }
                val qty = EditText(this).apply { hint = "تعداد"; inputType = 2; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY) }
                val price = EditText(this).apply { hint = "قیمت خرید هر سهم (ریال)"; inputType = 2; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY) }
                body.addView(symbol); body.addView(qty); body.addView(price)
                body.addView(Button(this).apply { text = "افزودن به سبد"
                    setOnClickListener {
                        try {
                            val q = qty.text.toString().toLong(); val p = price.text.toString().toDouble()
                            require(symbol.text.isNotBlank() && q > 0 && p > 0)
                            val old = prefs.getString("holdings", "") ?: ""
                            val line = "${symbol.text},$q,$p"
                            prefs.edit().putString("holdings", listOf(old, line).filter { it.isNotBlank() }.joinToString("\n")).apply()
                            show("سبد")
                        } catch (_: Exception) { card("خطا", "نماد، تعداد و قیمت معتبر وارد کنید") }
                    }
                })
                card("دارایی‌های ثبت‌شده", prefs.getString("holdings", "")?.ifBlank { "هنوز دارایی ثبت نشده است" } ?: "هنوز دارایی ثبت نشده است")
            }
            else -> {
                card("قابلیت‌های نسخه", "• موتور محاسباتی RSI، EMA، MACD، ATR و رنکو\n• محاسبه اندازه موقعیت بر اساس ریسک و حدضرر\n• اسکنر OHLCV با CSV یا دریافت تاریخچه تعدیل‌شده از TSETMC (حداقل ۵۰ کندل)\n• ثبت محلی دارایی‌ها\n• رابط فارسی\n\nدر دست توسعه: اسکن خودکار کل بازار برای افق ۲ تا ۷ روز، تابلوخوانی، اطلاعات بنیادی، همگام‌سازی و خروجی پشتیبان.")
                card("محدودیت", "این بیلد نسخه کامل نهایی نیست. اتصال تاریخچه TSETMC از API عمومی و غیررسمی استفاده می‌کند و هنوز تست میدانی/پایداری آن تأیید نشده است. هیچ داده بنیادی یا سیگنال زنده‌ای جعل نمی‌شود.")
            }
        }
    }

    private fun loadMarket() {
        status.text = "در حال اتصال به TSETMC…"
        thread {
            try {
                val http = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000; readTimeout = 15000
                    setRequestProperty("User-Agent", "Mozilla/5.0 BourseYarAIPro/0.2")
                    setRequestProperty("Accept", "application/json")
                }
                val code = http.responseCode
                val raw = (if (code in 200..299) http.inputStream else http.errorStream).bufferedReader().use { it.readText() }
                http.disconnect()
                runOnUiThread {
                    status.text = "پاسخ HTTP $code | داده هنوز اعتبارسنجی نشده"
                    body.removeAllViews()
                    card("نتیجه اتصال", "کد HTTP: $code\nحجم پاسخ: ${raw.length} نویسه")
                    card("پاسخ خام محدودشده", raw.take(5000).ifBlank { "پاسخ خالی" })
                    card("یادآوری", "این پاسخ هنوز به نماد/قیمت قابل معامله تبدیل نشده است؛ برای جلوگیری از خطا، سیگنال معاملاتی تولید نمی‌شود.")
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "اتصال ناموفق"; card("خطا", e.localizedMessage ?: "خطای شبکه") }
            }
        }
    }
}
