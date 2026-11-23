package com.bourseyar.ai.pro

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

data class PortfolioHolding(val symbol:String,val shares:Long,val buyPrice:Long,val currentPrice:Long,val stopLoss:Long=0) {
    val cost:Long get()=shares*buyPrice
    val value:Long get()=shares*currentPrice
    val pnl:Long get()=value-cost
    val returnPercent:Double get()=if(cost>0) pnl.toDouble()*100/cost else 0.0
}
class PortfolioActivity:Activity() {
    private val prefs by lazy { getSharedPreferences("bourseyar_portfolio_v1",MODE_PRIVATE) }
    private val bg=0xFF0D1826.toInt(); private val panel=0xFF18283A.toInt()
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
    private fun text(s:String,size:Float=14f,color:Int=0xFFFFFFFF.toInt(),bold:Boolean=false)=TextView(this).apply {
        text=s; textSize=size; setTextColor(color); if(bold)setTypeface(null,1); setPadding(6,8,6,8)
    }
    private fun render() {
        val data=load(); val total=data.sumOf{it.value}; val cost=data.sumOf{it.cost}; val pnl=total-cost
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,18,16,12);setBackgroundColor(bg)}
        root.addView(text("سبد سهام من",24f,0xFFFFFFFF.toInt(),true))
        root.addView(text("ارزش روز: "+fmt(total)+" ریال",19f,0xFFFFFFFF.toInt(),true))
        root.addView(text("سرمایه اولیه: "+fmt(cost)+" ریال",14f,0xFFBFCBDA.toInt()))
        root.addView(text("سود/زیان: "+fmt(pnl)+" ریال | "+percent(if(cost>0)pnl.toDouble()*100/cost else 0.0),16f,if(pnl>=0)green else red,true))
        root.addView(Button(this).apply{ text="افزودن سهم";setOnClickListener{form(null)} })
        root.addView(text("تعداد سهام ثبت‌شده: "+data.size,13f,0xFFBFCBDA.toInt()))
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val scroll=ScrollView(this).apply{addView(list)}
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        data.forEach { h -> list.addView(card(h)) }
        setContentView(root)
    }
    private fun card(h:PortfolioHolding):LinearLayout {
        val d=decision(h)
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,12,12,12);setBackgroundColor(panel)}
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
                if(s.isNotBlank()&&q>0&&b>0&&c>0) save(load().filterNot{it.symbol.equals(s,true)}+PortfolioHolding(s,q,b,c,sl))
                else Toast.makeText(this,"اطلاعات معتبر وارد کنید.",Toast.LENGTH_SHORT).show()
            }.show()
    }
}
