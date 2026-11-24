package com.bourseyar.ai.pro

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Candle(val close: Double, val high: Double = close, val low: Double = close, val volume: Double = 0.0, val open: Double = close)
data class RenkoBrick(val close: Double, val direction: Int)
data class ScanCandidate(val symbol: String, val report: TechnicalReport, val riskPercent: Double, val target1: Double, val target2: Double)
data class BacktestSummary(val holdingDays: Int, val trades: Int, val winRatePercent: Double, val averageReturnPercent: Double, val profitFactor: Double, val maxDrawdownPercent: Double)

data class VolumeMetrics(val averagePriorVolume: Double, val currentVolume: Double, val relativeVolume: Double, val validPriorSessions: Int)

object VolumeAnalysis {
    /** Uses up to 20 prior sessions and requires at least 10 valid positive-volume observations. */
    fun analyze(candles: List<Candle>, lookback: Int = 20, minimumValidSessions: Int = 10): VolumeMetrics? {
        if (lookback < 1 || minimumValidSessions < 1 || minimumValidSessions > lookback || candles.size < 2) return null
        val current = candles.last().volume
        if (!current.isFinite() || current <= 0.0) return null
        val prior = candles.dropLast(1).takeLast(lookback).map { it.volume }.filter { it.isFinite() && it > 0.0 }
        if (prior.size < minimumValidSessions) return null
        val average = prior.average()
        if (!average.isFinite() || average <= 0.0) return null
        val ratio = current / average
        if (!ratio.isFinite() || ratio <= 0.0) return null
        return VolumeMetrics(average, current, ratio, prior.size)
    }
}

data class TechnicalReport(
    val last: Double, val rsi: Double?, val ema20: Double?, val ema50: Double?,
    val macd: Double?, val macdSignal: Double?, val atr: Double?,
    val support: Double?, val resistance: Double?, val trend: String,
    val score: Int, val notes: List<String>
)

/** Deterministic indicators; no fabricated fundamental data or trade guarantees. */
object AnalysisEngine {
    /** Normalized individual-money-flow score (0..100); null when inputs are incomplete or invalid. */
    fun normalizedFlowScore(netIndividualValue: Double, grossIndividualValue: Double, positiveDays: Int, validDays: Int): Int? {
        if (!netIndividualValue.isFinite() || !grossIndividualValue.isFinite() ||
            grossIndividualValue <= 0.0 || positiveDays < 0 || validDays <= 0 || positiveDays > validDays ||
            abs(netIndividualValue) > grossIndividualValue * 1.000001) return null
        val pressurePercent = 100.0 * netIndividualValue / grossIndividualValue
        val pressureScore = (pressurePercent.coerceIn(-100.0, 100.0) + 100.0) / 2.0
        val consistencyScore = positiveDays.toDouble() / validDays * 100.0
        return (0.7 * pressureScore + 0.3 * consistencyScore).toInt().coerceIn(0, 100)
    }

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
    fun scanCandles(symbol: String, candles: List<Candle>, minAverageVolume: Double = 0.0): ScanCandidate? {
        if (symbol.isBlank() || candles.size < 50 || !minAverageVolume.isFinite() || minAverageVolume < 0) return null
        if (candles.any { !it.close.isFinite() || !it.high.isFinite() || !it.low.isFinite() ||
                !it.volume.isFinite() || !it.open.isFinite() || it.close <= 0 || it.open <= 0 || it.low <= 0 || it.high < max(it.open, max(it.close, it.low)) || it.low > min(it.open, it.close) || it.volume < 0 }) return null
        val report = analyze(candles) ?: return null
        val last = candles.last().close
        val prior = candles.dropLast(1).takeLast(20)
        if (prior.isEmpty()) return null
        val breakout = last > prior.maxOf { it.high }
        val volumeMetrics = VolumeAnalysis.analyze(candles)
        val avgVol20 = volumeMetrics?.averagePriorVolume
        if (minAverageVolume > 0 && avgVol20 != null && avgVol20 < minAverageVolume) return null
        val volRatio = volumeMetrics?.relativeVolume
        val momentum5 = (last / candles[candles.size - 6].close - 1.0) * 100.0
        val risk = (report.atr ?: last * 0.04).coerceAtLeast(last * 0.01)
        // Missing or insufficient volume data is neutral, never an artificial penalty or boost.
        val volumeBonus = when { volRatio == null -> 0; volRatio >= 1.5 -> 8; volRatio >= 1.0 -> 3; else -> -4 }
        val score = (report.score + (if (breakout) 10 else 0) + volumeBonus +
            (if (momentum5 in 0.5..8.0) 8 else if (momentum5 < -3.0) -8 else 0)).coerceIn(0, 100)
        val notes = report.notes + listOf(
            if (breakout) "شکست سقف ۲۰ کندل قبلی تأیید شد" else "شکست سقف ۲۰ کندل قبلی تأیید نشد",
            avgVol20?.let { "میانگین حجم جلسات معتبر از ۲۰ جلسه قبل: %.0f سهم".format(java.util.Locale.US, it) } ?: "حجم نسبی ناموجود؛ حداقل ۱۰ جلسه معتبر لازم است",
            volRatio?.let { "حجم آخرین جلسه نسبت به میانگین: %.2fx".format(java.util.Locale.US, it) } ?: "تأیید حجمی انجام نشد؛ داده کافی نیست",
            if (minAverageVolume > 0) "فیلتر حداقل میانگین حجم %.0f سهم اعمال شد".format(java.util.Locale.US, minAverageVolume) else "فیلتر حداقل حجم غیرفعال است",
            "بازده ۵ کندل اخیر: %.2f%%".format(java.util.Locale.US, momentum5),
            "فیلتر حجم، ارزش معاملات و وضعیت نماد را جایگزین بررسی نقدشوندگی نمی‌کند"
        )
        val ranked = report.copy(score = score, notes = notes)
        return ScanCandidate(symbol.trim().uppercase(), ranked, risk / last * 100.0,
            target(last, risk, 1.5), target(last, risk, 2.0))
    }

    /**
     * Walk-forward simulation: signal uses candles through day t; entry is next day's open.
     * Stop/target are entry-relative using signal-day ATR; scanner score gates the simulated entry. If both touch on one candle, stop is assumed first.
     * Positions may overlap. Fee is round-trip basis points.
     */
    fun backtest(candles: List<Candle>, minScore: Int = 70, feeBpsRoundTrip: Double = 0.0): List<BacktestSummary> {
        if (candles.size < 60 || minScore !in 0..100 || !feeBpsRoundTrip.isFinite() || feeBpsRoundTrip < 0) return emptyList()
        if (candles.any { !it.close.isFinite() || !it.open.isFinite() || !it.high.isFinite() || !it.low.isFinite() ||
                it.close <= 0 || it.open <= 0 || it.low <= 0 || it.high < max(it.open, max(it.close, it.low)) ||
                it.low > min(it.open, it.close) || !it.volume.isFinite() || it.volume < 0 }) return emptyList()
        return (2..7).map { horizon ->
            val returns = mutableListOf<Double>()
            for (signalIndex in 49 until candles.size - horizon) {
                val signalHistory = candles.subList(0, signalIndex + 1)
                val candidate = scanCandles("BACKTEST", signalHistory) ?: continue
                if (candidate.report.score < minScore) continue
                val report = candidate.report
                val entryIndex = signalIndex + 1
                val entry = candles[entryIndex].open
                val atrRisk = (report.atr ?: report.last * 0.04).coerceAtLeast(report.last * 0.01)
                // Anchor stop and target to the actual next-session entry, not the prior close.
                val stop = entry - atrRisk
                val targetPrice = entry + atrRisk * 1.5
                if (entry <= 0 || stop <= 0) continue
                var exit = candles[minOf(entryIndex + horizon - 1, candles.lastIndex)].close
                for (day in entryIndex..minOf(entryIndex + horizon - 1, candles.lastIndex)) {
                    val bar = candles[day]
                    if (bar.open <= stop) { exit = bar.open; break }
                    if (bar.open >= targetPrice) { exit = targetPrice; break }
                    val hitStop = bar.low <= stop
                    val hitTarget = bar.high >= targetPrice
                    if (hitStop) { exit = stop; break }
                    if (hitTarget) { exit = targetPrice; break }
                }
                returns += (exit / entry - 1.0) * 100.0 - feeBpsRoundTrip / 100.0
            }
            if (returns.isEmpty()) BacktestSummary(horizon, 0, 0.0, 0.0, 0.0, 0.0)
            else {
                val gains = returns.filter { it > 0 }.sum()
                val losses = -returns.filter { it < 0 }.sum()
                var equity = 1.0
                var peak = 1.0
                var maxDd = 0.0
                returns.forEach { r ->
                    equity *= (1.0 + r / 100.0)
                    peak = max(peak, equity)
                    maxDd = max(maxDd, (peak - equity) / peak * 100.0)
                }
                BacktestSummary(horizon, returns.size, returns.count { it > 0 } * 100.0 / returns.size,
                    returns.average(), if (losses > 0) gains / losses else if (gains > 0) Double.POSITIVE_INFINITY else 0.0, maxDd)
            }
        }
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
