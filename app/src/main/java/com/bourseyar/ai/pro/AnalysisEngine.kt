package com.bourseyar.ai.pro

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Candle(val close: Double, val high: Double = close, val low: Double = close, val volume: Double = 0.0)
data class RenkoBrick(val close: Double, val direction: Int)
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
