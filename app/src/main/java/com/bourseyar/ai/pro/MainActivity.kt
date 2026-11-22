package com.bourseyar.ai.pro

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import java.net.URL
import kotlin.concurrent.thread


class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var output: TextView
    private val endpoint = "https://cdn.tsetmc.com/api/ClosingPrice/GetMarketWatch?market=0&priceType=1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 28, 24, 24); setBackgroundColor(Color.rgb(13, 24, 38)) }
        val title = TextView(this).apply { text = "بورس‌یار AI Pro"; textSize = 25f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0,0,0,18) }
        status = TextView(this).apply { text = "آماده دریافت داده"; textSize = 16f; setTextColor(Color.rgb(255,202,92)); gravity = Gravity.CENTER; setPadding(0,0,0,14) }
        val refresh = Button(this).apply { text = "دریافت داده بازار"; setOnClickListener { refreshMarket() } }
        output = TextView(this).apply { text = "برای شروع، دریافت داده بازار را لمس کنید.\n\nاین نسخه اولیه است؛ داده‌ها قبل از استفاده معاملاتی باید بررسی شوند."; textSize = 15f; setTextColor(Color.WHITE) }
        val scroll = ScrollView(this).apply { addView(output) }
        root.addView(title); root.addView(status); root.addView(refresh); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
    }

    private fun refreshMarket() {
        status.text = "در حال اتصال به منبع داده…"
        output.text = "درخواست ارسال شد: $endpoint\n"
        thread {
            try {
                val connection = URL(endpoint).openConnection()
                connection.connectTimeout = 10000; connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 BourseYarAIPro/0.1")
                connection.setRequestProperty("Accept", "application/json")
                val code = (connection as java.net.HttpURLConnection).responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                runOnUiThread {
                    status.text = if (code in 200..299) "پاسخ دریافت شد (HTTP $code) — اعتبارسنجی لازم است" else "خطای سرویس (HTTP $code)"
                    output.text = "HTTP: $code\n\nپاسخ خام سرویس (ابتدای متن):\n" + body.take(12000) + "\n\nاین پاسخ هنوز تبدیل/اعتبارسنجی نشده و سیگنال خرید نیست."
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "اتصال ناموفق"; output.text = "خطا: ${e.localizedMessage ?: e.javaClass.simpleName}\n\nداده زنده تأیید نشده؛ هیچ سیگنال معاملاتی ارائه نمی‌شود." }
            }
        }
    }
}
