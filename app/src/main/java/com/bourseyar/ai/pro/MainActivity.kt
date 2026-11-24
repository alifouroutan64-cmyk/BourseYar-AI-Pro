package com.bourseyar.ai.pro

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private val endpoint = "https://cdn.tsetmc.com/api/ClosingPrice/GetMarketWatch?market=0&industrialGroup=&paperTypes%5B0%5D=1&paperTypes%5B1%5D=2&paperTypes%5B2%5D=3&paperTypes%5B3%5D=4&paperTypes%5B4%5D=5&paperTypes%5B5%5D=6&paperTypes%5B6%5D=7&paperTypes%5B7%5D=8&paperTypes%5B8%5D=9&showTraded=false&withBestLimits=false&hEven=0&RefID=0"
    private val marketHandler = Handler(Looper.getMainLooper())
    private var marketPolling = false
    private var marketRequestInFlight = false
    private var marketFailures = 0
    private var currentPage = ""
    private lateinit var marketResultsText: TextView
    private var lastMarketErrorReport: String = "هنوز گزارش خطایی ثبت نشده است."
    private val dashboardHandler = Handler(Looper.getMainLooper())
    private var dashboardPolling = false
    private var dashboardRequestInFlight = false
    private lateinit var dashboardSymbolInput: EditText
    private lateinit var dashboardResultsText: TextView
    private lateinit var dashboardAutoButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg; window.navigationBarColor = bg
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 20, 18, 12); setBackgroundColor(bg) }
        val title = label("بورس‌یار AI Pro", 25f, Color.WHITE, true)
        title.gravity = Gravity.CENTER
        status = label("نسخه توسعه‌ای | داده بازار هنوز اعتبارسنجی نشده", 12f, accent)
        status.gravity = Gravity.CENTER
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("بازار", "تابلو", "تحلیل", "اسکنر", "سبد", "راهنما").forEach { name ->
            nav.addView(Button(this).apply { text = name; textSize = 11f; setOnClickListener {
                if (name == "سبد") startActivity(android.content.Intent(this@MainActivity, PortfolioActivity::class.java)) else show(name)
            } }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            isSmoothScrollingEnabled = true
            addView(body)
        }
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

    override fun onStart() {
        super.onStart()
        if (currentPage == "بازار") startMarketPolling()
        if (currentPage == "تابلو") startDashboardPolling()
    }

    override fun onStop() {
        stopMarketPolling()
        stopDashboardPolling()
        super.onStop()
    }

    private fun startMarketPolling() {
        if (marketPolling) return
        marketPolling = true
        loadMarket()
    }

    private fun stopMarketPolling() {
        marketPolling = false
        marketHandler.removeCallbacksAndMessages(null)
    }

    private fun scheduleMarketPoll() {
        if (!marketPolling) return
        val multiplier = 1L shl marketFailures.coerceIn(0, 4)
        val delay = (5_000L * multiplier).coerceAtMost(60_000L)
        marketHandler.postDelayed({ if (marketPolling && currentPage == "بازار") loadMarket() }, delay)
    }

    private fun startDashboardPolling() {
        if (dashboardPolling) return
        dashboardPolling = true
        if (this::dashboardAutoButton.isInitialized) dashboardAutoButton.text = "توقف تازه‌سازی خودکار"
        loadInstrumentDashboard()
    }

    private fun stopDashboardPolling() {
        dashboardPolling = false
        dashboardHandler.removeCallbacksAndMessages(null)
    }

    private fun scheduleDashboardPoll() {
        if (!dashboardPolling || currentPage != "تابلو") return
        dashboardHandler.postDelayed({
            if (dashboardPolling && currentPage == "تابلو") loadInstrumentDashboard()
        }, 15_000L)
    }

    private fun loadInstrumentDashboard() {
        if (dashboardRequestInFlight || !this::dashboardSymbolInput.isInitialized ||
            !this::dashboardResultsText.isInitialized) return
        val symbol = dashboardSymbolInput.text.toString().trim()
        if (symbol.isBlank()) {
            dashboardResultsText.text = "نام نماد را وارد کنید."
            return
        }
        dashboardRequestInFlight = true
        dashboardResultsText.text = "در حال دریافت قیمت، دفتر سفارش، معاملات و جریان حقیقی/حقوقی برای $symbol…"
        Thread {
            val result = runCatching { TsetmcHistoryProvider.fetchLiveSnapshot(symbol) }
            runOnUiThread {
                dashboardRequestInFlight = false
                if (currentPage == "تابلو" && this::dashboardResultsText.isInitialized) {
                    val snapshot = result.getOrNull()
                    if (snapshot == null) {
                        dashboardResultsText.text = "دریافت تابلو ناموفق بود:\n${result.exceptionOrNull()?.message ?: "خطای نامشخص"}\n\nداده قبلی به‌عنوان زنده نمایش داده نمی‌شود."
                        status.text = "خطا در دریافت تابلو نماد"
                    } else {
                        val q = snapshot.quote
                        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        val quoteText = "نماد: ${snapshot.symbol} | وضعیت: ${q.state ?: "نامشخص"}\n" +
                            "آخرین معامله: ${q.last?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"} | پایانی: ${q.close?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"}\n" +
                            "تغییر: ${q.changePercent?.let { "%+.2f%%".format(java.util.Locale.US, it) } ?: "—"} | حجم: ${q.volume?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"} | تعداد معاملات: ${q.tradeCount?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"}"
                        val bookText = snapshot.orderBook.joinToString("\n") { level ->
                            "سطح ${level.level} | خرید: ${level.bidPrice?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"} / حجم ${level.bidVolume?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"} / سفارش ${level.bidCount?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"}" +
                                "   || فروش: ${level.askPrice?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"} / حجم ${level.askVolume?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"} / سفارش ${level.askCount?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"}"
                        }.ifBlank { "دفتر سفارش خالی است یا بازار در حال معامله نیست." }
                        val tradesText = snapshot.trades.joinToString("\n") { trade ->
                            "${trade.time} | قیمت ${trade.price?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"} | حجم ${trade.volume?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"} | تعداد ${trade.count?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"}"
                        }.ifBlank { "معامله‌ای در پاسخ اخیر موجود نیست." }
                        val flow = snapshot.clientFlow
                        val flowText = if (flow == null) "اطلاعات جریان حقیقی/حقوقی موجود نیست."
                            else "خالص حجم حقیقی: ${flow.netIndividualVolume?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"} | خالص حجم حقوقی: ${flow.netLegalVolume?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"}\n" +
                                "سرانه خرید حقیقی: ${flow.individualBuyPerBuyer?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"} | سرانه فروش حقیقی: ${flow.individualSellPerSeller?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"}"
                        dashboardResultsText.text = "آخرین دریافت موفق برنامه: $stamp\n\n$quoteText\n\nدفتر سفارش پنج‌سطحی\n$bookText\n\n۲۰ معامله اخیر\n$tradesText\n\nجریان حقیقی/حقوقی\n$flowText"
                        status.text = "تابلو $symbol دریافت شد | زمان دریافت $stamp"
                    }
                }
                scheduleDashboardPoll()
            }
        }.start()
    }

    private fun show(page: String) {
        currentPage = page
        if (page != "بازار") stopMarketPolling()
        if (page != "تابلو") stopDashboardPolling()
        body.removeAllViews()
        when (page) {
            "بازار" -> {
                card("دیده‌بان آنلاین بازار", "قیمت‌ها از دیده‌بان عمومی TSETMC دریافت می‌شوند. به‌روزرسانی خودکار هر ۵ ثانیه تلاش می‌شود؛ این فاصله تضمین تازگی یا تأخیر صفر نیست.")
                val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                controls.addView(Button(this).apply {
                    text = "به‌روزرسانی الآن"
                    setOnClickListener { loadMarket() }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                controls.addView(Button(this).apply {
                    text = if (marketPolling) "توقف خودکار" else "شروع خودکار"
                    setOnClickListener {
                        if (marketPolling) {
                            stopMarketPolling()
                            text = "شروع خودکار"
                            status.text = "به‌روزرسانی خودکار متوقف شد"
                        } else {
                            startMarketPolling()
                            text = "توقف خودکار"
                        }
                    }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                body.addView(controls)
                body.addView(Button(this).apply {
                    text = "عیب‌یابی اتصال به TSETMC"
                    setOnClickListener { diagnoseMarketConnection() }
                })
                body.addView(Button(this).apply {
                    text = "کپی گزارش خطا"
                    setOnClickListener { copyMarketErrorReport() }
                })
                marketResultsText = label("در انتظار دریافت اولین snapshot بازار…", 13f, Color.WHITE)
                body.addView(marketResultsText)
                card("محدودیت منبع", "منبع رایگان و بدون کلید API است، اما غیررسمی است؛ ممکن است از دسترس خارج شود، محدودیت نرخ داشته باشد یا داده با تأخیر برسد. در زمان خطا، داده قبلی به‌عنوان قیمت زنده نمایش داده نمی‌شود.")
                startMarketPolling()
                (controls.getChildAt(1) as Button).text = if (marketPolling) "توقف خودکار" else "شروع خودکار"
            }
            "تابلو" -> {
                card("تابلوخوانی زنده نماد", "قیمت، پنج سطح سفارش خرید و فروش، معاملات اخیر و جریان حقیقی/حقوقی از endpointهای عمومی TSETMC دریافت می‌شود. تازه‌سازی خودکار هر ۱۵ ثانیه تلاش می‌شود؛ زمان‌بندی تضمین دریافت لحظه‌ای نیست.")
                dashboardSymbolInput = EditText(this).apply {
                    hint = "نماد را وارد کنید؛ مثال: فولاد"
                    setText("فولاد")
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.LTGRAY)
                    isSingleLine = true
                }
                body.addView(dashboardSymbolInput)
                val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                controls.addView(Button(this).apply {
                    text = "دریافت تابلو"
                    setOnClickListener { loadInstrumentDashboard() }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                dashboardAutoButton = Button(this).apply {
                    text = "شروع خودکار"
                    setOnClickListener {
                        if (dashboardPolling) {
                            stopDashboardPolling()
                            text = "شروع خودکار"
                            status.text = "تازه‌سازی خودکار تابلو متوقف شد"
                        } else {
                            startDashboardPolling()
                            text = "توقف تازه‌سازی خودکار"
                        }
                    }
                }
                controls.addView(dashboardAutoButton, LinearLayout.LayoutParams(0, -2, 1f))
                body.addView(controls)
                dashboardResultsText = label("برای دریافت اطلاعات نماد، دکمه دریافت تابلو را بزنید.", 13f, Color.WHITE)
                body.addView(dashboardResultsText)
                card("نکته مهم دفتر سفارش", "داده bestLimits ممکن است در زمان تعطیلی بازار خالی باشد. سفارش‌های نمایش‌داده‌شده تضمین اجرای معامله نیستند.")
                card("محدودیت داده", "سرویس TSETMC عمومی و غیررسمی است؛ ممکن است از خارج ایران مسدود یا محدود شود. اگر دریافت ناموفق بود، شبکه را بررسی کنید. اطلاعات این صفحه توصیه خرید یا فروش نیست.")
                startDashboardPolling()
            }
            "تحلیل" -> {
                card("تحلیل تکنیکال نماد", "نام نماد را وارد کنید تا تاریخچه روزانه OHLCV از TSETMC دریافت و شاخص‌ها به‌صورت خودکار محاسبه شوند. حداقل ۵۰ کندل معتبر لازم است.")
                val symbolInput = EditText(this).apply {
                    hint = "نماد؛ مثال: فولاد"
                    setText("فولاد")
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.LTGRAY)
                    isSingleLine = true
                }
                body.addView(symbolInput)
                val feeInput = EditText(this).apply {
                    hint = "کارمزد رفت‌وبرگشت بک‌تست (واحد پایه؛ پیش‌فرض ۳۰)"
                    setText("30")
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.LTGRAY)
                    isSingleLine = true
                }
                body.addView(feeInput)
                val resultText = label("برای دریافت تحلیل، نماد را وارد و دکمه را بزنید.", 13f, Color.WHITE)
                body.addView(Button(this).apply {
                    text = "دریافت تاریخچه و تحلیل خودکار"
                    setOnClickListener {
                        val symbol = symbolInput.text.toString().trim()
                        if (symbol.isBlank()) {
                            resultText.text = "ابتدا نام نماد را وارد کنید."
                            return@setOnClickListener
                        }
                        val feeBps = feeInput.text.toString().toDoubleOrNull()
                            ?.takeIf { it.isFinite() && it >= 0.0 }
                        if (feeBps == null) {
                            resultText.text = "کارمزد رفت‌وبرگشت باید عدد نامنفی باشد."
                            return@setOnClickListener
                        }
                        isEnabled = false
                        text = "در حال دریافت تاریخچه…"
                        resultText.text = "در حال دریافت کندل‌های روزانه $symbol از TSETMC…"
                        Thread {
                            val result = runCatching {
                                val history = TsetmcHistoryProvider.fetchDaily(symbol)
                                val report = AnalysisEngine.analyze(history.candles)
                                    ?: error("برای محاسبه شاخص‌ها داده کافی نیست")
                                val backtest = AnalysisEngine.backtest(history.candles, 70, feeBps)
                                val backtestText = if (backtest.isEmpty() || backtest.all { it.trades == 0 }) {
                                    "بک‌تست: داده کافی یا معامله واجد شرایط وجود ندارد؛ حداقل ۶۰ کندل معتبر لازم است."
                                } else {
                                    "بک‌تست walk-forward با حداقل امتیاز ۷۰/۱۰۰:\n" + backtest.joinToString("\n") { b ->
                                        "${b.holdingDays} جلسه | ${b.trades} معامله | برد %.1f%% | میانگین بازده %.2f%% | ضریب سود %s | افت سرمایه %.2f%%".format(
                                            java.util.Locale.US, b.winRatePercent, b.averageReturnPercent,
                                            if (b.profitFactor.isFinite()) "%.2f".format(java.util.Locale.US, b.profitFactor) else "بدون زیان",
                                            b.maxDrawdownPercent)
                                    } + "\nکارمزد رفت‌وبرگشت: $feeBps واحد پایه؛ در هر افق فقط یک موقعیت هم‌زمان شبیه‌سازی می‌شود؛ ورود بعدی پس از خروج قبلی است."
                                }
                                val volume = VolumeAnalysis.analyze(history.candles)
                                val volumeText = volume?.let {
                                    "حجم آخرین کندل: %.0f سهم\nمیانگین حجم تا ۲۰ جلسه قبل: %.0f سهم\nحجم نسبی: %.2fx\nجلسات معتبر در میانگین: %d"
                                        .format(java.util.Locale.US, it.currentVolume, it.averagePriorVolume, it.relativeVolume, it.validPriorSessions)
                                } ?: "تحلیل حجم: ناموجود؛ حداقل ۱۰ جلسه معتبر و حجم مثبت لازم است"
                                val details = "نماد: ${history.symbol}\nتعداد کندل معتبر: ${history.candles.size}\n" +
                                    "آخرین قیمت پایانی تعدیل‌شده: ${"%.0f".format(java.util.Locale.US, report.last)}\n" +
                                    "روند: ${report.trend} | امتیاز تکنیکال: ${report.score}/100\n" +
                                    "RSI(14): ${report.rsi?.let { "%.2f".format(java.util.Locale.US, it) } ?: "—"}\n" +
                                    "EMA20: ${report.ema20?.let { "%.2f".format(java.util.Locale.US, it) } ?: "—"}\n" +
                                    "EMA50: ${report.ema50?.let { "%.2f".format(java.util.Locale.US, it) } ?: "—"}\n" +
                                    "MACD: ${report.macd?.let { "%.4f".format(java.util.Locale.US, it) } ?: "—"} | سیگنال: ${report.macdSignal?.let { "%.4f".format(java.util.Locale.US, it) } ?: "—"}\n" +
                                    "ATR(14): ${report.atr?.let { "%.2f".format(java.util.Locale.US, it) } ?: "—"}\n" +
                                    "حمایت تقریبی ۲۰ جلسه: ${report.support?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"}\n" +
                                    "مقاومت تقریبی ۲۰ جلسه: ${report.resistance?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—"}\n\n" +
                                    volumeText + "\n\n" + backtestText + "\n\n" + report.notes.joinToString("\n") +
                                    "\n\nتوجه: تاریخچه نمودار TSETMC با قیمت تعدیل‌شده دریافت می‌شود؛ قیمت فعلی تابلو ممکن است با قیمت تعدیل‌شده تاریخی متفاوت باشد. این خروجی توصیه خرید یا فروش نیست."
                                history.symbol to details
                            }
                            runOnUiThread {
                                isEnabled = true
                                text = "دریافت تاریخچه و تحلیل خودکار"
                                resultText.text = result.getOrNull()?.second
                                    ?: "تحلیل نماد انجام نشد: ${result.exceptionOrNull()?.message ?: "خطای نامشخص"}"
                                status.text = if (result.isSuccess) "تحلیل تاریخی ${result.getOrNull()?.first} دریافت شد" else "دریافت تاریخچه تحلیل ناموفق"
                            }
                        }.start()
                    }
                })
                body.addView(resultText)
                body.addView(Button(this).apply {
                    text = "جستجوی اطلاعیه‌های کدال برای نماد"
                    setOnClickListener {
                        val symbol = symbolInput.text.toString().trim()
                        if (symbol.isBlank()) {
                            resultText.text = "ابتدا نماد را وارد کنید."
                            return@setOnClickListener
                        }
                        isEnabled = false
                        text = "در حال جستجو در کدال…"
                        resultText.text = "در حال دریافت اطلاعیه‌های منتشرشده برای $symbol…"
                        Thread {
                            val result = runCatching { CodalPublicApi.searchFilings(symbol, 30) }
                            runOnUiThread {
                                isEnabled = true
                                text = "جستجوی اطلاعیه‌های کدال برای نماد"
                                val filing = result.getOrNull()
                                if (filing != null) {
                                    val content = android.text.SpannableStringBuilder()
                                    content.append("نماد: ${filing.symbol}\nتعداد اطلاعیه‌های بازگشتی: ${filing.filingsFound}\n\n")
                                    if (filing.recentFilings.isNotEmpty()) {
                                        filing.recentFilings.forEachIndexed { index, item ->
                                            val start = content.length
                                            content.append("${index + 1}. [${item.category}] ${item.title}")
                                            item.publishDate?.let { content.append(" | $it") }
                                            item.url?.let { url ->
                                                content.setSpan(
                                                    android.text.style.URLSpan(url),
                                                    start,
                                                    content.length,
                                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                                )
                                            }
                                            content.append("\n")
                                        }
                                    } else {
                                        content.append(filing.recentFilingTitles.mapIndexed { index, title -> "${index + 1}. $title" }
                                            .joinToString("\n").ifBlank { "عنوان اطلاعیه‌ای از پاسخ کدال استخراج نشد." })
                                    }
                                    content.append("\nلینک‌های قابل کلیک فقط به دامنه امن codal.ir محدود شده‌اند.")
                                    content.append("\n\nاین بخش فهرست اطلاعیه‌ها را نمایش می‌دهد؛ هنوز ارقام صورت‌های مالی از متن گزارش‌ها استخراج نشده‌اند.")
                                    resultText.text = content
                                    resultText.movementMethod = android.text.method.LinkMovementMethod.getInstance()
                                } else {
                                    resultText.text = "جستجوی کدال ناموفق بود: ${result.exceptionOrNull()?.message ?: "خطای نامشخص"}"
                                }
                            }
                        }.start()
                    }
                })
                card("محدودیت داده و تفسیر", "تاریخچه OHLCV از endpoint عمومی و غیررسمی TSETMC دریافت می‌شود و ممکن است محدود یا قطع شود. داده نمودار تعدیل‌شده است؛ برای مقایسه قیمت با تابلو باید این تفاوت لحاظ شود. امتیاز الگوریتمی تضمین بازده نیست.")
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
                val comprehensiveScanButton = Button(this).apply {
                    text = "اسکن جامع رایگان: تکنیکال + جریان پول + بنیادی"
                    setOnClickListener {
                        isEnabled = false
                        text = "در حال تحلیل چندمنبعی بازار…"
                        status.text = "دریافت تاریخچه، تابلو، جریان پول و اطلاعات بنیادی در حال انجام است"
                        Thread {
                            val result = runCatching { ComprehensiveMarketScanner.scan(30, 6) }
                            runOnUiThread {
                                isEnabled = true
                                text = "اجرای اسکن جامع رایگان بازار"
                                val candidates = result.getOrNull()
                                if (candidates == null) {
                                    card("اسکن جامع ناموفق", result.exceptionOrNull()?.message ?: "خطای نامشخص")
                                    status.text = "اسکن جامع ناموفق"
                                } else {
                                    val json = org.json.JSONArray()
                                    candidates.forEach { c ->
                                        json.put(org.json.JSONObject()
                                            .put("symbol", c.symbol)
                                            .put("price", c.lastPrice)
                                            .put("score", c.compositeScore)
                                            .put("technical", c.technicalScore)
                                            .put("flow", c.flowScore ?: org.json.JSONObject.NULL)
                                            .put("relativeVolume", c.relativeVolume ?: org.json.JSONObject.NULL)
                                            .put("stop", c.stopLoss)
                                            .put("target1", c.target1)
                                            .put("target2", c.target2)
                                            .put("eps", c.fundamental?.eps ?: org.json.JSONObject.NULL)
                                            .put("pe", c.fundamental?.pe ?: org.json.JSONObject.NULL)
                                            .put("recommendation", c.recommendation)
                                            .put("excludedReason", c.excludedReason ?: org.json.JSONObject.NULL))
                                    }
                                    prefs.edit().putString("comprehensive_scan", json.toString())
                                        .putLong("comprehensive_scan_time", System.currentTimeMillis()).apply()
                                    val eligible = candidates.filter { it.excludedReason == null }
                                    card("نتیجه اسکن جامع | ${candidates.size} نماد",
                                        candidates.joinToString("\n\n") { c ->
                                            "${c.symbol} | امتیاز کل ${c.compositeScore}/100 | تکنیکال ${c.technicalScore}/100 | ${c.recommendation}\n" +
                                            "قیمت: ${"%.0f".format(java.util.Locale.US, c.lastPrice)} | جریان پول: ${c.flowScore ?: "ناموجود"} | حجم نسبی: ${c.relativeVolume?.let { "%.2fx".format(java.util.Locale.US, it) } ?: "ناموجود"}\n" +
                                             "بنیادی: EPS ${c.fundamental?.eps?.let { "%.2f".format(java.util.Locale.US, it) } ?: "ناموجود"} | P/E ${c.fundamental?.pe?.let { "%.2f".format(java.util.Locale.US, it) } ?: "ناموجود"}\n" +
                                            "حدضرر: ${"%.0f".format(java.util.Locale.US, c.stopLoss)} | هدف ۱: ${"%.0f".format(java.util.Locale.US, c.target1)} | هدف ۲: ${"%.0f".format(java.util.Locale.US, c.target2)}\n" +
                                            (c.excludedReason?.let { "⛔ حذف از خرید: $it\n" } ?: "✅ از فیلتر اجرایی عبور کرده\n") +
                                            c.dataWarnings.joinToString("؛ ")
                                        }.ifBlank { "هیچ نمادی تحلیل معتبر دریافت نکرد." })
                                    val diagnostics = ComprehensiveMarketScanner.lastDiagnostics
                                    val diagnosticText = "نمادهای درخواستی: ${diagnostics.requestedSymbols} | تحلیل موفق: ${diagnostics.analyzedSymbols} | فاقد تاریخچه کافی: ${diagnostics.rejectedForInsufficientHistory} | خطای دریافت/پردازش: ${diagnostics.failedCount}" +
                                        if (diagnostics.failedSymbols.isEmpty()) "\nهمه نمادهای انتخاب‌شده با خطای ثبت‌شده‌ای مواجه نشدند."
                                        else "\nنمونه خطاها:\n" + diagnostics.failedSymbols.take(6).joinToString("\n")
                                    card("گزارش عیب‌یابی اسکن", diagnosticText)
                                    card("بهترین گزینه فعلی",
                                        eligible.firstOrNull()?.let { "${it.symbol} | امتیاز ${it.compositeScore}/100 | ${it.recommendation}\nاین پیشنهاد در بخش سبد نیز قابل مشاهده خواهد بود." }
                                            ?: "هیچ سهمی شرایط ورود قابل اجرا را ندارد؛ از خرید اجباری خودداری کنید.")
                                    status.text = "اسکن جامع انجام شد | ${diagnostics.analyzedSymbols} نماد تحلیل شد؛ ${diagnostics.failedCount} خطا/کمبود داده"
                                }
                            }
                        }.start()
                    }
                }
                body.addView(comprehensiveScanButton)
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
                body.addView(Button(this).apply {
                    text = "اسکن خودکار ۱۰ نماد برتر بازار (۲ تا ۷ روزه)"
                    setOnClickListener {
                        val minAvgVol = minVolumeInput.text.toString().toDoubleOrNull()
                        val minScore = scoreInput.text.toString().toIntOrNull()
                        val feeBps = feeInput.text.toString().toDoubleOrNull()
                        if (minAvgVol == null || !minAvgVol.isFinite() || minAvgVol < 0 || minScore == null || minScore !in 0..100 || feeBps == null || !feeBps.isFinite() || feeBps < 0) {
                            card("تنظیمات نامعتبر", "فیلتر حجم، امتیاز و کارمزد را بررسی کنید")
                        } else {
                            isEnabled = false
                            text = "در حال دریافت ۱۰ تاریخچه روزانه…"
                            status.text = "اسکن خودکار بازار شروع شد؛ دریافت‌ها با فاصله انجام می‌شوند…"
                            Thread {
                                try {
                                    val leaders = TsetmcHistoryProvider.fetchMarketLeaders(20)
                                    val selected = leaders.take(10)
                                    val histories = mutableListOf<HistoricalSeries>()
                                    val errors = mutableListOf<String>()
                                    selected.forEachIndexed { index, item ->
                                        try {
                                            histories += TsetmcHistoryProvider.fetchDaily(item.symbol)
                                        } catch (e: Exception) {
                                            errors += item.symbol + ": " + (e.message ?: "تاریخچه نامعتبر")
                                        }
                                        if (index < selected.lastIndex) Thread.sleep(650)
                                    }
                                    require(histories.isNotEmpty()) { "هیچ تاریخچه معتبری دریافت نشد. " + errors.joinToString("؛ ") }
                                    val flowScores = histories.take(5).associate { data ->
                                        data.symbol to runCatching { TsetmcHistoryProvider.fetchFiveSessionFlowScore(data.symbol) }.getOrNull()
                                    }
                                    val ranked = histories.mapNotNull { data ->
                                        val candidate = AnalysisEngine.scanCandles(data.symbol, data.candles, minAvgVol) ?: return@mapNotNull null
                                        val relativeVolume = VolumeAnalysis.analyze(data.candles)?.relativeVolume
                                        val stop = candidate.report.last * (1.0 - candidate.riskPercent / 100.0)
                                        val risk = candidate.report.last - stop
                                        val rr = if (risk > 0.0) (candidate.target1 - candidate.report.last) / risk else null
                                        val flow = flowScores[data.symbol]
                                        val total = OpportunityScoring.score(candidate.report.score, flow, relativeVolume, rr)
                                        Triple(candidate, total, Pair(relativeVolume, flow))
                                    }.sortedByDescending { it.second ?: -1 }
                                    val bt = histories.associate { it.symbol to AnalysisEngine.backtest(it.candles, minScore, feeBps) }
                                    runOnUiThread {
                                        symbolsInput.setText(leaders.joinToString("، ") { it.symbol })
                                        card("اسکن خودکار بازار | ۱۰ نماد برتر ارزش معاملات",
                                            ranked.joinToString("\n\n") { row ->
                                                val c = row.first
                                                "${c.symbol} | امتیاز ترکیبی ${row.second ?: "—"}/100 | تکنیکال ${c.report.score}/100 | ${c.report.trend}\n" +
                                                "حجم نسبی: ${row.third.first?.let { "%.2fx".format(java.util.Locale.US, it) } ?: "ناموجود"} | جریان پول ۵ جلسه: ${row.third.second ?: "ناموجود"}\n" +
                                                "قیمت تعدیل‌شده: ${c.report.last} | RSI: ${c.report.rsi ?: "—"} | ATR: ${c.report.atr ?: "—"}\n" +
                                                "حدضرر آزمایشی: ${c.report.last * (1.0 - c.riskPercent / 100.0)} | هدف ۱.۵R: ${c.target1} | هدف ۲R: ${c.target2}\n" +
                                                c.report.notes.joinToString("؛ ")
                                            }.ifBlank { "هیچ نمادی از فیلترهای فعلی عبور نکرد." } +
                                            "\n\nتاریخچه ۱۰ نماد اول ارزش معاملات دریافت شد؛ جریان پول فقط برای حداکثر ۵ نماد اول تلاش می‌شود.")
                                        bt.forEach { (symbol, rows) ->
                                            card("بک‌تست $symbol | افق ۲ تا ۷ جلسه", rows.joinToString("\n") { b ->
                                                "${b.holdingDays} روز: ${b.trades} معامله | برد %.1f%% | میانگین %.2f%% | PF %.2f | افت %.2f%%".format(java.util.Locale.US, b.winRatePercent, b.averageReturnPercent, b.profitFactor, b.maxDrawdownPercent)
                                            } + "\nکارمزد رفت‌وبرگشت: $feeBps bps؛ معاملات هم‌پوشان‌اند.")
                                        }
                                        if (errors.isNotEmpty()) card("نمادهای ناموفق", errors.joinToString("\n"))
                                        card("محدودیت اسکن", "این غربالگری بر اساس ۱۰ نماد اول ارزش معاملات گزارش‌شده است، نه تمام بازار. داده‌های TSETMC عمومی و غیررسمی‌اند؛ حجم نسبی و امتیاز ترکیبی احتمال سود نیستند و وضعیت توقف/صف/عمق سفارش‌ها هنوز به‌صورت کامل بررسی نمی‌شود.")
                                        status.text = "اسکن تمام شد | ${histories.size}/${selected.size} تاریخچه معتبر"
                                        (body.findViewWithTag<Button>("marketWideScanButton"))?.let { it.isEnabled = true; it.text = "اجرای دوباره اسکن خودکار بازار" }
                                    }
                                } catch (e: Exception) {
                                    runOnUiThread {
                                        card("خطا در اسکن خودکار بازار", e.message ?: "خطای شبکه یا ساختار پاسخ")
                                        status.text = "اسکن بازار ناموفق بود"
                                        (body.findViewWithTag<Button>("marketWideScanButton"))?.let { it.isEnabled = true; it.text = "تلاش مجدد اسکن خودکار بازار" }
                                    }
                                }
                            }.start()
                        }
                    }
                }.apply { tag = "marketWideScanButton" })
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
                                        val relativeVolume = VolumeAnalysis.analyze(data.candles)?.relativeVolume
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

    private fun firstNumber(row: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            if (!row.has(key) || row.isNull(key)) continue
            val value = row.opt(key)
            val number = when (value) {
                is Number -> value.toDouble()
                is String -> value.replace(",", "").toDoubleOrNull()
                else -> null
            }
            if (number != null && number.isFinite()) return number
        }
        return null
    }

    private fun firstText(row: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val value = row.optString(key).trim()
            if (value.isNotBlank() && value != "null") return value
        }
        return null
    }

    private fun firstPositiveNumber(row: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            val number = firstNumber(row, key)
            if (number != null && number > 0.0) return number
        }
        return null
    }

    // Prefer a populated alias over an earlier zero field; preserve zero only if all aliases are zero.
    private fun firstActivityNumber(row: JSONObject, vararg keys: String): Double? {
        var firstZero: Double? = null
        for (key in keys) {
            val number = firstNumber(row, key) ?: continue
            if (number > 0.0) return number
            if (number == 0.0 && firstZero == null) firstZero = number
        }
        return firstZero
    }

    /**
     * Diagnostic-only request: reports DNS, HTTP status, content type and JSON shape.
     * It never treats a successful TCP/HTTP response as proof of valid live market data.
     */
    private fun diagnoseMarketConnection() {
        if (marketRequestInFlight) {
            if (this::marketResultsText.isInitialized) marketResultsText.text = "درخواست قبلی هنوز در حال اجراست؛ چند لحظه دیگر دوباره امتحان کنید."
            return
        }
        marketRequestInFlight = true
        if (this::marketResultsText.isInitialized) marketResultsText.text = "در حال بررسی DNS و پاسخ مستقیم TSETMC…"
        thread {
            val report = try {
                val host = "cdn.tsetmc.com"
                val addresses = runCatching {
                    java.net.InetAddress.getAllByName(host).joinToString { it.hostAddress }
                }.getOrElse { "خطای DNS: ${it.message ?: it.javaClass.simpleName}" }
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 20000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                    setRequestProperty("Accept", "application/json, text/plain, */*")
                    setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
                    setRequestProperty("Referer", "https://www.tsetmc.com/")
                    setRequestProperty("Cache-Control", "no-cache")
                }
                try {
                    val code = connection.responseCode
                    val contentType = connection.contentType ?: "نامشخص"
                    val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    val shape = runCatching {
                        val json = JSONObject(body)
                        val keys = json.keys().asSequence().toList().take(30).joinToString(", ")
                        val rows = json.optJSONArray("marketwatch") ?: json.optJSONArray("marketWatch")
                        "کلیدهای JSON: ${keys.ifBlank { "ندارد" }} | تعداد ردیف marketwatch: ${rows?.length() ?: "کلید پیدا نشد"}"
                    }.getOrElse { "JSON قابل‌تجزیه نیست: ${it.message ?: it.javaClass.simpleName}" }
                    "نتیجه عیب‌یابی\nDNS: $addresses\nHTTP: $code\nContent-Type: $contentType\nحجم پاسخ: ${body.length} نویسه\n$shape\n\nنمونه پاسخ:\n${body.take(700).ifBlank { "(خالی)" }}"
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                "عیب‌یابی ناموفق\nنوع خطا: ${e.javaClass.simpleName}\nشرح: ${e.localizedMessage ?: "بدون توضیح"}"
            }
            runOnUiThread {
                marketRequestInFlight = false
                if (currentPage == "بازار" && this::marketResultsText.isInitialized) {
                    marketResultsText.text = report
                    status.text = "عیب‌یابی اتصال انجام شد؛ نتیجه را بررسی کنید"
                }
            }
        }
    }

    private fun copyMarketErrorReport() {
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("گزارش خطای بورس‌یار", lastMarketErrorReport))
        Toast.makeText(this, "گزارش خطا کپی شد؛ آن را در گفتگو ارسال کنید.", Toast.LENGTH_LONG).show()
    }

    private fun loadMarket() {
        if (marketRequestInFlight) return
        marketRequestInFlight = true
        if (currentPage == "بازار") status.text = "در حال دریافت snapshot بازار از TSETMC…"
        thread {
            var failure: String? = null
            var rendered = ""
            var diagnostic = "گزارش خطای دریافت بازار\n"
            try {
                val http = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 BourseYarAIPro/0.3")
                    setRequestProperty("Accept", "application/json")
                }
                val code = try { http.responseCode } catch (e: Exception) { http.disconnect(); throw e }
                val contentType = http.contentType ?: "نامشخص"
                val stream = if (code in 200..299) http.inputStream else http.errorStream
                val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                http.disconnect()
                diagnostic += "نشانی: $endpoint\nHTTP: $code\nنوع محتوا: $contentType\nحجم پاسخ: ${raw.length} نویسه\n"
                require(code in 200..299) { "TSETMC پاسخ HTTP " + code + " برگرداند" }
                val root = JSONObject(raw)
                val rows = root.optJSONArray("marketwatch")
                    ?: root.optJSONArray("marketWatch")
                    ?: error("آرایه marketwatch در پاسخ پیدا نشد؛ ساختار سرویس تغییر کرده است")
                diagnostic += "کلیدهای ریشه: ${root.keys().asSequence().toList().joinToString()}\nتعداد ردیف خام: ${rows.length()}\n"
                diagnostic += "نمونه سه ردیف اول:\n" + (0 until minOf(3, rows.length())).joinToString("\n---\n") { i -> rows.optJSONObject(i)?.toString() ?: "ردیف $i شیء JSON نیست" }.take(3500) + "\n"
                var objectRows = 0
                var symbolRows = 0
                var commonStockRows = 0
                var priceRows = 0
                val parsed = (0 until rows.length()).mapNotNull { index ->
                    val row = rows.optJSONObject(index) ?: return@mapNotNull null
                    objectRows++
                    val symbol = firstText(row, "lVal18AFC", "lva", "symbol", "instrumentName") ?: return@mapNotNull null
                    symbolRows++
                    // yVal=300 is common stock. Do not mix options, debt and other instruments into the equity watchlist.
                    val instrumentType = firstNumber(row, "yVal", "yval")?.toInt()
                    if (instrumentType != 300) return@mapNotNull null
                    commonStockRows++
                    if (commonStockRows <= 5) diagnostic += "نمونه سهام عادی $commonStockRows: " + row.toString().take(1800) + "\n"
                    // TSETMC market-watch has both descriptive and compact field names.
                    // Zero in a descriptive field must not mask a populated compact alias.
                    // Full descriptive fields may be zero in this compact MarketWatch response.
                    // Compact aliases from the real payload are used as fallbacks.
                    val last = firstPositiveNumber(row, "pDrCotVal", "pl", "lastPrice", "last", "pdv")
                        ?: return@mapNotNull null
                    priceRows++
                    val closeCandidate = firstPositiveNumber(row, "pClosing", "pcl", "closingPrice", "close")
                    // Reject a likely wrong alias rather than displaying a closing price far from last price.
                    val close = closeCandidate?.takeIf { kotlin.math.abs(it - last) / last <= 0.10 }
                    val previousClose = firstPositiveNumber(row, "priceYesterday", "py")
                    // Daily percentage is based on closing price, not the last traded price.
                    val changeBase = close ?: last
                    val change = previousClose?.let { 100.0 * (changeBase - it) / it }
                    val volume = firstActivityNumber(row, "qTotTran5J", "tvol", "volume", "qtj")
                    val value = firstActivityNumber(row, "qTotCap", "tval", "tradedValue", "qtc")
                    val trades = firstActivityNumber(row, "zTotTran", "tno", "tradeCount", "ztt")
                    val instrumentId = firstText(row, "insCode", "inscode", "instrumentId", "instrumentID")
                    listOf<Any?>(symbol, last, close, change, volume, value, trades, instrumentId)
                }.distinctBy { item ->
                    // Deduplicate only identical instrument IDs. Same display symbol with
                    // different IDs is retained and surfaced in diagnostics.
                    (item.getOrNull(7) as? String)?.let { "id:$it" } ?: "row:${item.hashCode()}"
                }.sortedByDescending { it[5] as? Double ?: 0.0 }
                val duplicateCount = priceRows - parsed.size
                diagnostic += "\nردیف‌های شیء JSON: $objectRows\nردیف‌های دارای نماد: $symbolRows\nسهام عادی با yVal=300: $commonStockRows\nردیف‌های دارای قیمت مثبت: $priceRows\nتکراری‌های حذف‌شده با شناسه یکسان: $duplicateCount\nردیف‌های نهایی معتبر سهام عادی: ${parsed.size}\n"
                lastMarketErrorReport = diagnostic
                require(parsed.isNotEmpty()) { "پاسخ دریافت شد اما ردیف قیمت معتبر در آن نبود. خام: ${rows.length()}، شیء: $objectRows، نماددار: $symbolRows، دارای قیمت: $priceRows" }
                val shown = parsed.take(45)
                val lines = shown.mapIndexed { index, item ->
                    val symbol = item[0] as String
                    val last = item[1] as Double
                    val close = item[2] as? Double
                    val change = item[3] as? Double
                    val volume = item[4] as? Double
                    val value = item[5] as? Double
                    val trades = item.getOrNull(6) as? Double
                    val instrumentId = item.getOrNull(7) as? String
                    val lowTradeWarning = trades != null && trades < 20.0
                    val changeText = change?.let { "%+.2f%%".format(java.util.Locale.US, it) } ?: "درصد ناموجود"
                    // Keep quote data and activity metrics on separate lines. Long mixed
                    // Persian/Latin strings wrap in Android TextView with confusing bidi order.
                    (index + 1).toString() + ". " + symbol +
                        " | آخرین: " + "%.0f".format(java.util.Locale.US, last) +
                        " | پایانی: " + (close?.let { "%.0f".format(java.util.Locale.US, it) } ?: "—") +
                        " | تغییر: " + changeText + "\n" +
                        "حجم: " + (volume?.let { "%.0f".format(java.util.Locale.US, it) } ?: "ناموجود") +
                        " | ارزش: " + (value?.let { "%.0f".format(java.util.Locale.US, it) } ?: "ناموجود") +
                        " | تعداد معاملات: " + (trades?.let { "%.0f".format(java.util.Locale.US, it) } ?: "ناموجود") +
                        (if (lowTradeWarning) " | هشدار: معاملات بسیار کم" else "")
                }
                rendered = "تعداد ردیف معتبر: " + parsed.size + " | نمایش: " + shown.size + "\n\n" + lines.joinToString("\n")
            } catch (e: Exception) {
                failure = e.localizedMessage ?: "خطای شبکه یا پاسخ نامعتبر"
                diagnostic += "\nنوع استثنا: ${e.javaClass.name}\nشرح خطا: ${e.localizedMessage ?: "بدون توضیح"}\n"
                lastMarketErrorReport = diagnostic
            }
            runOnUiThread {
                marketRequestInFlight = false
                if (failure == null) {
                    marketFailures = 0
                    if (currentPage == "بازار" && this::marketResultsText.isInitialized) {
                        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        marketResultsText.text = "آخرین دریافت موفق: " + stamp + "\n\n" + rendered
                        status.text = "اتصال برقرار | دیده‌بان TSETMC | تلاش برای تازه‌سازی دوره‌ای"
                    }
                } else {
                    marketFailures = (marketFailures + 1).coerceAtMost(4)
                    if (currentPage == "بازار" && this::marketResultsText.isInitialized) {
                        marketResultsText.text = "دریافت ناموفق: " + failure + "\nگزارش کامل را با دکمه «کپی گزارش خطا» بردارید.\nداده قبلی به‌عنوان زنده نگه‌داری نمی‌شود."
                        status.text = "اتصال ناموفق | تلاش مجدد با فاصله افزایشی"
                    }
                }
                scheduleMarketPoll()
            }
        }
    }
}