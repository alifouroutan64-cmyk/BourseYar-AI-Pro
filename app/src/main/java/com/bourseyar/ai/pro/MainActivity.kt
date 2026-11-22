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
                body.addView(Button(this).apply {
                    text = "اعتبارسنجی و اسکن CSV"
                    setOnClickListener {
                        try {
                            val series = MarketCsvParser.parse(input.text.toString())
                            val results = series.mapNotNull { AnalysisEngine.scanCandles(it.symbol, it.candles) }
                                .sortedByDescending { it.report.score }
                            require(results.isNotEmpty()) { "هیچ سری معتبر برای اسکن پیدا نشد" }
                            card("نتیجه اسکن | ${results.size} نماد", results.joinToString("\\n\\n") { x ->
                                "${x.symbol} | امتیاز ${x.report.score}/100 | روند ${x.report.trend}\\n" +
                                "آخرین قیمت: ${x.report.last}\\nRSI: ${x.report.rsi ?: "—"} | ATR: ${x.report.atr ?: "—"}\\n" +
                                "حدضرر آزمایشی ATR: ${x.report.last * (1.0 - x.riskPercent / 100.0)}\\n" +
                                "هدف ۱.۵R: ${x.target1} | هدف ۲R: ${x.target2}\\n" +
                                "ریسک فاصله حدضرر: %.2f%%".format(java.util.Locale.US, x.riskPercent) +
                                "\\n" + x.report.notes.joinToString("؛ ")
                            })
                            card("محدودیت مهم", "اسکنر از OHLCV و حجم نسبی استفاده می‌کند، اما صف، ارزش معاملات، توقف نماد، تعدیلات شرکتی و داده زنده را تأیید نمی‌کند. CSV باید از منبع معتبر و با قیمت‌های هم‌مقیاس تهیه شود؛ نتیجه توصیه قطعی خرید نیست.")
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
                card("قابلیت‌های نسخه", "• موتور محاسباتی RSI، EMA، MACD، ATR و رنکو\n• محاسبه اندازه موقعیت بر اساس ریسک و حدضرر\n• اسکنر آزمایشی سری قیمت‌های تاریخی (حداقل ۵۰ دوره)\n• ثبت محلی دارایی‌ها\n• رابط فارسی\n\nدر دست توسعه: دریافت تاریخچه OHLC معتبر، تابلوخوانی، اطلاعات بنیادی، اسکنر چندروزه، همگام‌سازی و خروجی پشتیبان.")
                card("محدودیت", "این بیلد نسخه کامل نهایی نیست. API عمومی TSETMC هنوز به parser پایدار و آزموده‌شده متصل نشده و هیچ داده بنیادی یا سیگنال زنده‌ای جعل نمی‌شود.")
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
