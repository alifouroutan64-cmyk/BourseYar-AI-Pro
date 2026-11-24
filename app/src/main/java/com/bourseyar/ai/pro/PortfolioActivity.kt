package com.bourseyar.ai.pro

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.text.InputType
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

private fun safeMultiply(a: Long, b: Long): Long = try {
    Math.multiplyExact(a, b)
} catch (_: ArithmeticException) {
    Long.MAX_VALUE
}

private fun safeAdd(a: Long, b: Long): Long = try {
    Math.addExact(a, b)
} catch (_: ArithmeticException) {
    if (a >= 0 && b >= 0) Long.MAX_VALUE else Long.MIN_VALUE
}

private fun safeSubtract(a: Long, b: Long): Long = try {
    Math.subtractExact(a, b)
} catch (_: ArithmeticException) {
    if (a >= 0 && b < 0) Long.MAX_VALUE else Long.MIN_VALUE
}

data class PortfolioHolding(val symbol:String,val shares:Long,val buyPrice:Long,val currentPrice:Long,val stopLoss:Long=0) {
    val cost:Long get()=safeMultiply(shares,buyPrice)
    val value:Long get()=safeMultiply(shares,currentPrice)
    val pnl:Long get()=safeSubtract(value,cost)
    val returnPercent:Double get()=if(cost>0) pnl.toDouble()*100/cost else 0.0
}
class PortfolioActivity:Activity() {
    private val prefs by lazy { getSharedPreferences("bourseyar_portfolio_v1",MODE_PRIVATE) }
    private val bg=0xFF07101F.toInt(); private val panel=0xFF152941.toInt()
    private val green=0xFF43D391.toInt(); private val red=0xFFFF6975.toInt(); private val amber=0xFFFFC857.toInt()
    private fun load():List<PortfolioHolding> = try {
        val a=JSONArray(prefs.getString("list","[]"))
        (0 until a.length()).map { i -> val o=a.getJSONObject(i)
            PortfolioHolding(o.getString("symbol"),o.getLong("shares"),o.getLong("buy"),o.getLong("current"),o.optLong("stop",0)) }
    } catch(_:Exception){ emptyList() }
    private fun save(data:List<PortfolioHolding>) {
        val a=JSONArray()
        data.forEach { h -> a.put(JSONObject().put("symbol",h.symbol).put("shares",h.shares).put("buy",h.buyPrice).put("current",h.currentPrice).put("stop",h.stopLoss)) }
        prefs.edit().putString("list",a.toString()).apply(); render()
    }
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); render() }
    private fun fmt(n:Long)=NumberFormat.getNumberInstance(Locale("fa","IR")).format(n)
    private fun percent(n:Double)=String.format(Locale("fa","IR"),"%.2f%%",n)
    private fun shape(colors:IntArray,radius:Int,stroke:Int=0):GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR,colors).apply {
            cornerRadius=radius*resources.displayMetrics.density
            if(stroke>0)setStroke((stroke*resources.displayMetrics.density).toInt(),0xFF294764.toInt())
        }
    private fun text(s:String,size:Float=14f,color:Int=0xFFFFFFFF.toInt(),bold:Boolean=false)=TextView(this).apply {
        text=s; textSize=size; setTextColor(color)
        typeface=Typeface.create(if(bold)"sans-serif-medium" else "sans-serif",Typeface.NORMAL)
        setPadding(7,8,7,8); gravity=Gravity.CENTER_VERTICAL; setLineSpacing(2f,1.05f); setTextIsSelectable(true)
    }
    private fun polish(view:View) {
        when(view) {
            is Button -> {
                view.isAllCaps=false; view.textSize=12f; view.typeface=Typeface.create("sans-serif-medium",0)
                view.setTextColor(0xFFF3FAFF.toInt())
                view.background=shape(intArrayOf(0xFF1B5B70.toInt(),0xFF124057.toInt()),14,1)
                view.minimumHeight=(46*resources.displayMetrics.density).toInt()
                view.setPadding(12,6,12,6); view.elevation=2f
            }
            is EditText -> {
                view.textSize=14f; view.setTextColor(0xFFFFFFFF.toInt()); view.setHintTextColor(0xFF9DB2C9.toInt())
                view.background=shape(intArrayOf(0xFF0D1B2D.toInt(),0xFF0D1B2D.toInt()),13,1)
                view.setPadding(14,12,14,12)
            }
            is LinearLayout -> Unit
        }
        if(view is android.view.ViewGroup) for(i in 0 until view.childCount) polish(view.getChildAt(i))
    }
    private fun render() {
        val data=load(); val total=data.fold(0L){sum,h->safeAdd(sum,h.value)}; val cost=data.fold(0L){sum,h->safeAdd(sum,h.cost)}; val pnl=safeSubtract(total,cost)
        window.statusBarColor=0xFF050C18.toInt(); window.navigationBarColor=0xFF050C18.toInt()
        window.decorView.layoutDirection=View.LAYOUT_DIRECTION_RTL
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,16,16,10);background=shape(intArrayOf(0xFF081324.toInt(),bg),0)}
        val hero=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,16,18,16);background=shape(intArrayOf(0xFF173B55.toInt(),0xFF10263F.toInt()),22);elevation=5f}
        hero.addView(text("◈  سبد سرمایه‌گذاری من",22f,0xFFFFFFFF.toInt(),true))
        hero.addView(text("ارزش روز دارایی‌ها",12f,0xFFB8CDDF.toInt()))
        hero.addView(text(fmt(total)+" ریال",25f,0xFFFFFFFF.toInt(),true))
        hero.addView(text("سرمایه اولیه  "+fmt(cost)+" ریال",12f,0xFFB8CDDF.toInt()))
        hero.addView(text("سود/زیان  "+fmt(pnl)+" ریال  •  "+percent(if(cost>0)pnl.toDouble()*100/cost else 0.0),15f,if(pnl>=0)green else red,true))
        root.addView(hero)
        val actions=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,10,0,8)}
        listOf(
            "＋  افزودن سهم" to { form(null) },
            "▤  دفتر معاملات خرید و فروش" to { showLedger() },
            "✦  پیشنهادهای اسکن جامع بورس‌یار" to { showScannerRecommendations() },
            "↻  به‌روزرسانی قیمت‌ها از TSETMC" to { refreshPrices() }
        ).forEach { (caption,action) ->
            actions.addView(Button(this).apply{text=caption;setOnClickListener{action()}},LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=6})
        }
        root.addView(actions)
        root.addView(text("دارایی‌های ثبت‌شده  •  "+data.size+" نماد",14f,0xFF9DB2C9.toInt(),true))
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val scroll=ScrollView(this).apply{isFillViewport=false;isVerticalScrollBarEnabled=false;addView(list)}
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        data.forEach { h -> list.addView(card(h)) }
        setContentView(root)
        polish(root)
    }

    private fun loadTransactions(): List<LedgerTransaction> = try {
        val array = JSONArray(prefs.getString("transactions", "[]"))
        (0 until array.length()).map { i ->
            val row = array.getJSONObject(i)
            LedgerTransaction(
                timestamp = row.getLong("timestamp"),
                symbol = row.getString("symbol"),
                side = TransactionSide.valueOf(row.getString("side")),
                quantity = row.getLong("quantity"),
                unitPrice = row.getLong("price"),
                fee = row.optLong("fee", 0L)
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun saveTransactions(items: List<LedgerTransaction>) {
        val array = JSONArray()
        items.forEach { tx ->
            array.put(JSONObject()
                .put("timestamp", tx.timestamp)
                .put("symbol", tx.symbol)
                .put("side", tx.side.name)
                .put("quantity", tx.quantity)
                .put("price", tx.unitPrice)
                .put("fee", tx.fee))
        }
        prefs.edit().putString("transactions", array.toString()).apply()
    }

    private fun showLedger() {
        val transactions = loadTransactions()
        val result = PortfolioLedger.calculate(transactions)
        if (result == null) {
            AlertDialog.Builder(this).setMessage("دفتر معاملات نامعتبر است؛ فروش بیش از موجودی یا عدد خارج از محدوده ثبت شده است.")
                .setPositiveButton("بستن", null).show()
            return
        }
        val lines = result.positions.joinToString("\n") {
            "${it.symbol} | موجودی: ${fmt(it.quantity)} | میانگین بهای تمام‌شده: ${fmt(it.averageCostPerShare)} ریال | بهای مانده: ${fmt(it.remainingCost)} ریال"
        }
        val recent = transactions.sortedByDescending { it.timestamp }.take(10).joinToString("\n") {
            val side = if (it.side == TransactionSide.BUY) "خرید" else "فروش"
            "$side ${it.symbol} | ${fmt(it.quantity)} سهم | قیمت ${fmt(it.unitPrice)} | کارمزد ${fmt(it.fee)}"
        }
        val message = "این دفتر مستقل از ثبت دستی قدیمی سبد است.\n" +
            "تعداد معاملات ثبت‌شده: ${transactions.size}\nسود/زیان تحقق‌یافته: ${fmt(result.realizedProfitLoss)} ریال\n\nموجودی باز:\n" +
            lines.ifBlank { "موجودی باز ثبت نشده است." } +
            "\n\nآخرین معاملات:\n" + recent.ifBlank { "هنوز معامله‌ای ثبت نشده است." }
        val builder = AlertDialog.Builder(this).setTitle("دفتر معاملات")
            .setMessage(message)
            .setPositiveButton("ثبت معامله") { _, _ -> addLedgerTransaction() }
            .setNeutralButton("بستن", null)
        if (transactions.isNotEmpty()) {
            builder.setNegativeButton("حذف آخرین معامله") { _, _ ->
                AlertDialog.Builder(this).setMessage("آخرین معامله ثبت‌شده حذف شود؟")
                    .setNegativeButton("لغو", null)
                    .setPositiveButton("حذف") { _, _ ->
                        val latest = transactions.maxByOrNull { it.timestamp }
                        if (latest != null) {
                            val revised = transactions.toMutableList()
                            revised.remove(latest)
                            if (PortfolioLedger.calculate(revised) != null) {
                                saveTransactions(revised)
                                showLedger()
                            } else Toast.makeText(this, "حذف باعث نامعتبر شدن دفتر می‌شود.", Toast.LENGTH_LONG).show()
                        }
                    }.show()
            }
        }
        builder.show()
    }

    private fun addLedgerTransaction() {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 0, 12, 0)
        }
        val side = Spinner(this).apply {
            adapter = ArrayAdapter(this@PortfolioActivity, android.R.layout.simple_spinner_dropdown_item, listOf("خرید", "فروش"))
        }
        fun numeric(hint: String): EditText = EditText(this).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_NUMBER
            isSingleLine = true
        }
        val symbol = EditText(this).apply { hint = "نماد؛ مثال فولاد"; isSingleLine = true }
        val quantity = numeric("تعداد سهم")
        val price = numeric("قیمت هر سهم (ریال)")
        val fee = numeric("کارمزد کل معامله (ریال، اختیاری)")
        fields.addView(side)
        listOf(symbol, quantity, price, fee).forEach { fields.addView(it) }
        AlertDialog.Builder(this).setTitle("ثبت خرید یا فروش").setView(fields)
            .setNegativeButton("لغو", null)
            .setPositiveButton("ثبت") { _, _ ->
                val name = symbol.text.toString().trim().uppercase(Locale.ROOT)
                val qty = quantity.text.toString().toLongOrNull() ?: 0L
                val unitPrice = price.text.toString().toLongOrNull() ?: 0L
                val commission = fee.text.toString().toLongOrNull() ?: 0L
                val tx = LedgerTransaction(
                    System.currentTimeMillis(), name,
                    if (side.selectedItemPosition == 0) TransactionSide.BUY else TransactionSide.SELL,
                    qty, unitPrice, commission
                )
                val candidate = loadTransactions() + tx
                val calculated = PortfolioLedger.calculate(candidate)
                if (name.isBlank() || qty <= 0 || unitPrice <= 0 || commission < 0 || calculated == null) {
                    Toast.makeText(this, "اطلاعات نامعتبر است یا تعداد فروش از موجودی دفتر معاملات بیشتر است.", Toast.LENGTH_LONG).show()
                } else {
                    saveTransactions(candidate)
                    showLedger()
                }
            }.show()
    }

    private fun refreshPrices() {
        val holdings = load()
        if (holdings.isEmpty()) {
            Toast.makeText(this, "ابتدا سهمی به سبد اضافه کنید.", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "در حال دریافت قیمت نمادها از TSETMC…", Toast.LENGTH_SHORT).show()
        Thread {
            val updated = holdings.map { holding ->
                try {
                    val snapshot = TsetmcHistoryProvider.fetchLiveSnapshot(holding.symbol)
                    val livePrice = snapshot.quote.last ?: snapshot.quote.close
                    if (livePrice != null && livePrice > 0 && livePrice <= Long.MAX_VALUE.toDouble()) holding.copy(currentPrice = livePrice.toLong()) else holding
                } catch (_: Exception) { holding }
            }
            runOnUiThread {
                save(updated)
                val changed = updated.zip(holdings).count { (new, old) -> new.currentPrice != old.currentPrice }
                Toast.makeText(this, "قیمت $changed از ${holdings.size} نماد به‌روزرسانی شد. نمادهای ناموفق با قیمت قبلی باقی ماندند.", Toast.LENGTH_LONG).show()
            }
        }.start()
    }


    private fun showScannerRecommendations() {
        val scanPrefs = getSharedPreferences("bourseyar", MODE_PRIVATE)
        val raw = scanPrefs.getString("comprehensive_scan", null)
        if (raw.isNullOrBlank()) {
            AlertDialog.Builder(this@PortfolioActivity).setMessage("هنوز اسکن جامع اجرا نشده است. از صفحه اسکنر، تحلیل جامع بازار را اجرا کنید.")
                .setPositiveButton("متوجه شدم", null).show()
            return
        }
        try {
            val rows = JSONArray(raw)
            val eligible = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
                .filter { it.isNull("excludedReason") || it.optString("excludedReason").isBlank() }
                .sortedByDescending { it.optInt("score", 0) }
            val best = eligible.firstOrNull()
            val report = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
                .sortedByDescending { it.optInt("score", 0) }
                .joinToString("\n\n") { row ->
                    val symbol = row.optString("symbol")
                    val score = row.optInt("score")
                    val exclusion = if (row.isNull("excludedReason")) "" else "\n⛔ حذف از خرید: " + row.optString("excludedReason")
                    "$symbol | امتیاز $score/100 | " + row.optString("recommendation") +
                        "\nقیمت: " + row.optDouble("price").toLong() +
                        " | حد ضرر: " + row.optDouble("stop").toLong() +
                        " | هدف ۱: " + row.optDouble("target1").toLong() +
                        " | هدف ۲: " + row.optDouble("target2").toLong() + exclusion
                }
            val builder = AlertDialog.Builder(this@PortfolioActivity)
                .setTitle("نتایج اسکن جامع")
                .setMessage(report.ifBlank { "نتیجه‌ای ذخیره نشده است." })
                .setPositiveButton("بستن", null)
            if (best != null) {
                builder.setNeutralButton("افزودن بهترین گزینه به سبد") { _, _ ->
                    val qtyInput = EditText(this@PortfolioActivity).apply {
                        hint = "تعداد سهم برای ثبت"
                        inputType = InputType.TYPE_CLASS_NUMBER
                    }
                    AlertDialog.Builder(this@PortfolioActivity).setTitle("ثبت پیشنهادی ${best.optString("symbol")}")
                        .setMessage("قیمت ثبت اولیه از اسکن: ${best.optDouble("price").toLong()} ریال\nحد ضرر: ${best.optDouble("stop").toLong()} ریال\nاین ثبت فقط در سبد محلی انجام می‌شود؛ هیچ سفارش خریدی ارسال نمی‌شود.")
                        .setView(qtyInput).setNegativeButton("لغو", null)
                        .setPositiveButton("ثبت در سبد") { _, _ ->
                            val quantity = qtyInput.text.toString().toLongOrNull() ?: 0L
                            val price = best.optDouble("price").toLong()
                            val stop = best.optDouble("stop").toLong()
                            if (quantity > 0 && price > 0 && runCatching { Math.multiplyExact(quantity, price) }.isSuccess) {
                                save(load().filterNot { it.symbol.equals(best.optString("symbol"), true) } +
                                    PortfolioHolding(best.optString("symbol"), quantity, price, price, stop))
                            } else Toast.makeText(this@PortfolioActivity, "تعداد معتبر وارد کنید", Toast.LENGTH_SHORT).show()
                        }.show()
                }
            }
            builder.show()
        } catch (e: Exception) {
            AlertDialog.Builder(this@PortfolioActivity).setMessage("خواندن پیشنهادهای ذخیره‌شده ناموفق بود: ${e.message}")
                .setPositiveButton("بستن", null).show()
        }
    }

    private fun card(h:PortfolioHolding):LinearLayout {
        val d=decision(h)
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,12,12,12);background=shape(intArrayOf(0xFF152941.toInt(),0xFF0F1E31.toInt()),18,1);elevation=2f}
        box.addView(text(h.symbol,20f,0xFFFFFFFF.toInt(),true))
        box.addView(text("تعداد: "+fmt(h.shares)+" | خرید: "+fmt(h.buyPrice)+" | قیمت فعلی: "+fmt(h.currentPrice),13f))
        box.addView(text("ارزش: "+fmt(h.value)+" ریال",14f))
        box.addView(text("سود/زیان: "+fmt(h.pnl)+" ریال ("+percent(h.returnPercent)+")",15f,if(h.pnl>=0)green else red,true))
        box.addView(text(d.first,16f,d.second,true))
        box.addView(text(d.third,13f,0xFFD9E1EA.toInt()))
        box.addView(text(if(h.stopLoss>0)"حد ضرر: "+fmt(h.stopLoss)+" ریال" else "حد ضرر ثبت نشده",13f,amber))
        box.addView(Button(this).apply{ text="ویرایش سهم / قیمت";setOnClickListener{form(h)} })
        box.addView(Button(this).apply{ text="حذف سهم";setOnClickListener{
            AlertDialog.Builder(this@PortfolioActivity).setMessage("این سهم از سبد حذف شود؟")
                .setPositiveButton("حذف"){_,_->save(load().filterNot{it.symbol==h.symbol})}
                .setNegativeButton("لغو",null).show()
        }})
        val lp=LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,12);box.layoutParams=lp
        return box
    }
    private fun decision(h:PortfolioHolding):Triple<String,Int,String> = when {
        h.stopLoss>0 && h.currentPrice<=h.stopLoss -> Triple("هشدار حد ضرر / بررسی فروش",red,"قیمت به حد ضرر ثبت‌شده رسیده؛ بررسی خروج لازم است.")
        h.returnPercent>=20 -> Triple("افزایش فروش / کاهش موقعیت",amber,"سود به ۲۰٪ یا بیشتر رسیده؛ ذخیره بخشی از سود را بررسی کن.")
        h.returnPercent<=-12 -> Triple("هشدار زیان / بررسی خروج",red,"زیان از ۱۲٪ عبور کرده؛ بدون بررسی روند، میانگین کم نکن.")
        else -> Triple("نگهداری",0xFF82BFFF.toInt(),"فعلاً نگهداری؛ این تصمیم فقط بر اساس سود/زیان ثبت‌شده است، نه تحلیل زنده.")
    }
    private fun form(old:PortfolioHolding?) {
        val fields=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,0,12,0)}
        fun input(hint:String,value:String="",numeric:Boolean=true):EditText=EditText(this).apply{
            this.hint=hint;setText(value);if(numeric)inputType=InputType.TYPE_CLASS_NUMBER;isSingleLine=true
        }
        val symbol=input("نماد",old?.symbol.orEmpty(),false); val qty=input("تعداد سهم",old?.shares?.toString().orEmpty())
        val buy=input("قیمت خرید هر سهم (ریال)",old?.buyPrice?.toString().orEmpty()); val current=input("قیمت فعلی هر سهم (ریال)",old?.currentPrice?.toString().orEmpty())
        val stop=input("حد ضرر اختیاری (ریال)",old?.stopLoss?.takeIf{it>0}?.toString().orEmpty())
        listOf(symbol,qty,buy,current,stop).forEach{fields.addView(it)}
        AlertDialog.Builder(this).setTitle(if(old==null)"ثبت سهم" else "ویرایش سهم و قیمت").setView(fields)
            .setNegativeButton("لغو",null).setPositiveButton("ذخیره"){_,_->
                val s=symbol.text.toString().trim().uppercase(Locale.ROOT); val q=qty.text.toString().toLongOrNull()?:0
                val b=buy.text.toString().toLongOrNull()?:0; val c=current.text.toString().toLongOrNull()?:0; val sl=stop.text.toString().toLongOrNull()?:0
                if(s.isNotBlank()&&q>0&&b>0&&c>0&&sl>=0&&runCatching { Math.multiplyExact(q,b); Math.multiplyExact(q,c) }.isSuccess) save(load().filterNot{it.symbol.equals(s,true)}+PortfolioHolding(s,q,b,c,sl))
                else Toast.makeText(this,"اطلاعات معتبر وارد کنید؛ حاصل تعداد در قیمت نباید از ظرفیت عددی بیشتر شود.",Toast.LENGTH_LONG).show()
            }.show()
    }
}