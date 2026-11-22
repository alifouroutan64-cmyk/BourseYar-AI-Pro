package com.bourseyar.ai.pro

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Candle(val close: Double, val high: Double = close, val low: Double = close, val volume: Double = 0.0)
data class RenkoBrick(val close: Double, val direction: Int)
data class ScanCandidate(val symbol: String, val report: TechnicalReport, val riskPercent: Double, val target1: Double, val target2: Double)

data class TechnicalReport(
    val last: Double, val rsi: Double?, val ema20: Double?, val ema50: Double?,
    val macd: Double?, val macdSignal: Double?, val atr: Double?,
    val support: Double?, val resistance: Double?, val trend: String,
    val score: Int, val notes: List<String>
)

/** Deterministic indicators; no fabricated fundamental data or trade guarantees. */
object AnalysisEngine {
    fun ema(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty() || period < 1) return emptyList()
        val alpha = 2.0 / (period + 1.0)
        val out = ArrayList<Double>(values.size)
        var current = values.first()
        out.add(current)
        for (i in 1 until values.size) {
            current = values[i] * alpha + current * (1.0 - alpha)
            out.add(current)
        }
        return out
    }

    fun rsi(values: List<Double>, period: Int = 14): Double? {
        if (values.size <= period) return null
        var gain = 0.0; var loss = 0.0
        for (i in 1..period) {
            val d = values[i] - values[i - 1]
            if (d > 0) gain += d else loss -= d
        }
        var avgGain = gain / period; var avgLoss = loss / period
        for (i in period + 1 until values.size) {
            val d = values[i] - values[i - 1]
            avgGain = (avgGain * (period - 1) + max(d, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + max(-d, 0.0)) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - 100.0 / (1.0 + rs)
    }

    fun atr(candles: List<Candle>, period: Int = 14): Double? {
        if (candles.size <= period) return null
        val trs = (1 until candles.size).map { i ->
            val c = candles[i]; val prev = candles[i - 1].close
            max(c.high - c.low, max(abs(c.high - prev), abs(c.low - prev)))
        }
        return trs.takeLast(period).average()
    }

    fun analyze(candles: List<Candle>): TechnicalReport? {
        if (candles.size < 20) return null
        val closes = candles.map { it.close }
        val e20 = ema(closes, 20).last()
        val e50 = if (closes.size >= 50) ema(closes, 50).last() else null
        val macdLine = ema(closes, 12).zip(ema(closes, 26)) { a, b -> a - b }
        val signal = ema(macdLine, 9).lastOrNull()
        val macd = macdLine.lastOrNull()
        val r = rsi(closes)
        val a = atr(candles)
        val recent = candles.takeLast(20)
        val support = recent.minOf { it.low }
        val resistance = recent.maxOf { it.high }
        var score = 50
        val notes = mutableListOf<String>()
        if (closes.last() > e20) { score += 10; notes += "قیمت بالاتر از EMA20 است" } else { score -= 8; notes += "قیمت پایین‌تر از EMA20 است" }
        if (e50 != null && e20 > e50) { score += 10; notes += "چیدمان میانگین‌ها صعودی است" }
        if (r != null) {
            if (r in 45.0..65.0) { score += 8; notes += "RSI در محدوده متعادل متمایل به صعود است" }
            if (r > 75) { score -= 12; notes += "RSI بالاست؛ ریسک اصلاح وجود دارد" }
            if (r < 30) { notes += "RSI اشباع فروش را نشان می‌دهد؛ به‌تنهایی سیگنال خرید نیست" }
        }
        if (macd != null && signal != null) {
            if (macd > signal) { score += 8; notes += "MACD بالاتر از خط سیگنال است" }
            else { score -= 6; notes += "MACD پایین‌تر از خط سیگنال است" }
        }
        score = score.coerceIn(0, 100)
        val trend = when { closes.last() > e20 && (e50 == null || e20 > e50) -> "صعودی"; closes.last() < e20 -> "نزولی"; else -> "خنثی" }
        return TechnicalReport(closes.last(), r, e20, e50, macd, signal, a, support, resistance, trend, score, notes)
    }

    /** Historical close-only screening. Not a live signal or liquidity confirmation. */
    fun scanSeries(symbol: String, closes: List<Double>): ScanCandidate? {
        if (symbol.isBlank() || closes.size < 50 || closes.any { !it.isFinite() || it <= 0.0 }) return null
        val report = analyze(closes.map { Candle(it) }) ?: return null
        val last = closes.last()
        val priorHigh = closes.dropLast(1).takeLast(20).maxOrNull() ?: return null
        val momentum5 = (last / closes[closes.size - 6] - 1.0) * 100.0
        val breakout = last > priorHigh
        val risk = (report.atr ?: last * 0.04).coerceAtLeast(last * 0.01)
        val score = (report.score + (if (breakout) 10 else 0) +
            (if (momentum5 in 0.5..8.0) 8 else if (momentum5 < -3.0) -8 else 0)).coerceIn(0, 100)
        val notes = report.notes + listOf(
            if (breakout) "شکست سقف ۲۰ دوره‌ای در داده ورودی" else "شکست سقف ۲۰ دوره‌ای تأیید نشده",
            "بازده ۵ دوره اخیر: %.2f%%".format(java.util.Locale.US, momentum5),
            "حجم و نقدشوندگی بررسی نشده‌اند؛ این نتیجه فقط فهرست پیگیری است"
        )
        val ranked = report.copy(score = score, notes = notes)
        return ScanCandidate(symbol.trim().uppercase(), ranked, risk / last * 100.0,
            target(last, risk, 1.5), target(last, risk, 2.0))
    }

    /** OHLCV-aware historical scanner. Requires at least 50 daily candles. */
    fun scanCandles(symbol: String, candles: List<Candle>): ScanCandidate? {
        if (symbol.isBlank() || candles.size < 50) return null
        if (candles.any { !it.close.isFinite() || !it.high.isFinite() || !it.low.isFinite() ||
                !it.volume.isFinite() || it.close <= 0 || it.low <= 0 || it.high < max(it.close, it.low) || it.volume < 0 }) return null
        val report = analyze(candles) ?: return null
        val last = candles.last().close
        val prior = candles.dropLast(1).takeLast(20)
        if (prior.isEmpty()) return null
        val breakout = last > prior.maxOf { it.high }
        val avgVol20 = prior.takeLast(20).map { it.volume }.average()
        val volRatio = if (avgVol20 > 0) candles.last().volume / avgVol20 else 0.0
        val momentum5 = (last / candles[candles.size - 6].close - 1.0) * 100.0
        val risk = (report.atr ?: last * 0.04).coerceAtLeast(last * 0.01)
        val volumeBonus = if (volRatio >= 1.5) 8 else if (volRatio >= 1.0) 3 else -4
        val score = (report.score + (if (breakout) 10 else 0) + volumeBonus +
            (if (momentum5 in 0.5..8.0) 8 else if (momentum5 < -3.0) -8 else 0)).coerceIn(0, 100)
        val notes = report.notes + listOf(
            if (breakout) "شکست سقف ۲۰ کندل قبلی تأیید شد" else "شکست سقف ۲۰ کندل قبلی تأیید نشد",
            "حجم امروز نسبت به میانگین ۲۰ کندل: %.2fx".format(java.util.Locale.US, volRatio),
            "بازده ۵ کندل اخیر: %.2f%%".format(java.util.Locale.US, momentum5),
            "فیلتر حجم، ارزش معاملات و وضعیت نماد را جایگزین بررسی نقدشوندگی نمی‌کند"
        )
        val ranked = report.copy(score = score, notes = notes)
        return ScanCandidate(symbol.trim().uppercase(), ranked, risk / last * 100.0,
            target(last, risk, 1.5), target(last, risk, 2.0))
    }

    fun renko(closes: List<Double>, boxSize: Double): List<RenkoBrick> {
        if (closes.isEmpty() || boxSize <= 0) return emptyList()
        val bricks = mutableListOf<RenkoBrick>()
        var last = closes.first()
        for (price in closes.drop(1)) {
            var steps = ((price - last) / boxSize).toInt()
            while (steps > 0) { last += boxSize; bricks += RenkoBrick(last, 1); steps-- }
            steps = ((last - price) / boxSize).toInt()
            while (steps > 0) { last -= boxSize; bricks += RenkoBrick(last, -1); steps-- }
        }
        return bricks
    }

    fun positionSize(capital: Double, riskPercent: Double, entry: Double, stop: Double): Long {
        if (capital <= 0 || riskPercent <= 0 || entry <= stop || stop <= 0) return 0
        val riskCash = capital * riskPercent.coerceIn(0.1, 2.0) / 100.0
        return (riskCash / (entry - stop)).toLong().coerceAtLeast(0)
    }

    fun target(entry: Double, risk: Double, multiple: Double): Double = entry + risk * multiple
}
