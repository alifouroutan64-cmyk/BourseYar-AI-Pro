package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisEngineTest {
    @Test
    fun codalFilingTitlesAreClassifiedWithoutInventingMetrics() {
        assertEquals("صورت‌های مالی", classifyCodalFiling("ارائه صورت‌های مالی سالانه حسابرسی‌شده"))
        assertEquals("افزایش سرمایه", classifyCodalFiling("پیشنهاد افزایش سرمایه"))
        assertEquals("مجمع", classifyCodalFiling("آگهی دعوت به مجمع عمومی عادی"))
        assertEquals("پرداخت سود سهام", classifyCodalFiling("زمان‌بندی پرداخت سود سهام"))
        assertEquals("افشای اطلاعات بااهمیت", classifyCodalFiling("افشای اطلاعات بااهمیت"))
        assertEquals("سایر اطلاعیه‌ها", classifyCodalFiling("اطلاعیه نامشخص"))
    }

    @Test
    fun codalLinksOnlyAllowOfficialHttpsHosts() {
        assertEquals("https://www.codal.ir/Reports/Decision.aspx?id=1",
            safeCodalUrl("/Reports/Decision.aspx?id=1"))
        assertEquals("https://codal.ir/report",
            safeCodalUrl("https://codal.ir/report"))
        assertEquals(null, safeCodalUrl("http://www.codal.ir/report"))
        assertEquals(null, safeCodalUrl("https://codal.ir.example.com/report"))
        assertEquals(null, safeCodalUrl("https://example.com/report"))
        assertEquals(null, safeCodalUrl("https://codal.ir:8443/report"))
        assertEquals(null, safeCodalUrl("https://user@codal.ir/report"))
        assertEquals(null, safeCodalUrl("https://codal.ir.evil.example/report"))
        assertEquals("https://SUB.CODAL.IR/report", safeCodalUrl("https://SUB.CODAL.IR/report"))
    }

    @Test
    fun codalFilingReferenceKeepsOfficialExcelLinkSeparateFromReportPage() {
        val filing = CodalFilingReference(
            title = "صورت‌های مالی",
            publishDate = "۱۴۰۵/۰۷/۳۰",
            url = "https://www.codal.ir/Reports/Decision.aspx?LetterSerial=abc",
            excelUrl = "https://excel.codal.ir/service/Excel/GetAll/abc/0"
        )
        assertTrue(filing.hasExcel)
        assertEquals("https://excel.codal.ir/service/Excel/GetAll/abc/0", filing.excelUrl)
    }

    @Test
    fun codalExcelLinksOnlyAllowOfficialExcelHostOverHttps() {
        assertEquals("https://excel.codal.ir/service/Excel/GetAll/abc/0",
            safeCodalExcelUrl("https://excel.codal.ir/service/Excel/GetAll/abc/0"))
        assertEquals(null, safeCodalExcelUrl("http://excel.codal.ir/report"))
        assertEquals(null, safeCodalExcelUrl("https://evil-excel.codal.ir/report"))
        assertEquals(null, safeCodalExcelUrl("https://excel.codal.ir.example.com/report"))
        assertEquals(null, safeCodalExcelUrl("https://user@excel.codal.ir/report"))
        assertEquals(null, safeCodalExcelUrl("https://excel.codal.ir:8443/report"))
        assertEquals(null, safeCodalExcelUrl("https://www.codal.ir/report"))
    }

    private fun flatCandles(size: Int = 90) = List(size) {
        Candle(open = 100.0, high = 101.0, low = 99.0, close = 100.0, volume = 1000.0)
    }

    @Test
    fun backtestReturnsAllHoldingHorizons() {
        val results = AnalysisEngine.backtest(flatCandles(), minScore = 0)
        assertEquals(listOf(2, 3, 4, 5, 6, 7), results.map { it.holdingDays })
        assertTrue(results.all { it.trades > 0 })
    }

    @Test
    fun backtestDoesNotOverlapPositionsWithinEachHorizon() {
        val results = AnalysisEngine.backtest(flatCandles(90), minScore = 0)
        val expectedTrades = mapOf(2 to 20, 3 to 13, 4 to 10, 5 to 8, 6 to 6, 7 to 5)
        results.forEach { result ->
            assertEquals(expectedTrades[result.holdingDays], result.trades)
        }
    }

    @Test
    fun roundTripFeeReducesAverageReturn() {
        val noFee = AnalysisEngine.backtest(flatCandles(), minScore = 0, feeBpsRoundTrip = 0.0)
        val withFee = AnalysisEngine.backtest(flatCandles(), minScore = 0, feeBpsRoundTrip = 100.0)
        assertTrue(withFee.zip(noFee).all { (fee, free) -> fee.averageReturnPercent < free.averageReturnPercent })
    }

    @Test
    fun invalidOhlcIsRejected() {
        val candles = flatCandles().toMutableList()
        candles[60] = Candle(open = 100.0, high = 98.0, low = 99.0, close = 100.0, volume = 1000.0)
        assertTrue(AnalysisEngine.backtest(candles, minScore = 0).isEmpty())
    }

    @Test
    fun normalizedFlowScoreRewardsPositivePressureAndConsistency() {
        val strong = AnalysisEngine.normalizedFlowScore(100.0, 100.0, 5, 5)
        val weak = AnalysisEngine.normalizedFlowScore(-100.0, 100.0, 0, 5)
        assertTrue(strong != null && weak != null && strong > weak)
        assertEquals(100, strong)
        assertEquals(0, weak)
    }

    @Test
    fun normalizedFlowScoreRejectsInvalidOrIncompleteTotals() {
        assertEquals(null, AnalysisEngine.normalizedFlowScore(10.0, 0.0, 1, 2))
        assertEquals(null, AnalysisEngine.normalizedFlowScore(110.0, 100.0, 1, 2))
        assertEquals(null, AnalysisEngine.normalizedFlowScore(Double.NaN, 100.0, 1, 2))
    }

    @Test
    fun insufficientHistoryIsRejected() {
        assertTrue(AnalysisEngine.backtest(flatCandles(59), minScore = 0).isEmpty())
    }

    @Test
    fun opportunityScoreCombinesTechnicalFlowVolumeAndRewardRisk() {
        val full = OpportunityScoring.score(80, 100, 2.0, 2.5)
        assertEquals(90, full)
    }

    @Test
    fun opportunityScoreRenormalizesWhenFlowIsUnavailable() {
        val withMissingFlow = OpportunityScoring.score(80, null, 2.0, 2.5)
        assertEquals(86, withMissingFlow)
    }

    @Test
    fun opportunityScoreRejectsInvalidInputs() {
        assertEquals(null, OpportunityScoring.score(101, 50, 1.0, 2.0))
        assertEquals(null, OpportunityScoring.score(70, 50, Double.NaN, 2.0))
        assertEquals(null, OpportunityScoring.score(70, 50, 1.0, -1.0))
    }
    @Test
    fun opportunityScoreRewardsHigherRelativeVolumeOnlyAsOneFactor() {
        val lowVolume = OpportunityScoring.score(80, 80, 0.5, 2.0)
        val normalVolume = OpportunityScoring.score(80, 80, 1.0, 2.0)
        val strongVolume = OpportunityScoring.score(80, 80, 2.0, 2.0)
        assertTrue(lowVolume != null && normalVolume != null && strongVolume != null)
        assertTrue(requireNotNull(lowVolume) < requireNotNull(normalVolume))
        assertTrue(requireNotNull(normalVolume) < requireNotNull(strongVolume))
    }

    @Test
    fun volumeAnalysisRequiresTenValidPriorSessions() {
        val candles = flatCandles(21).mapIndexed { index, candle -> if (index < 12) candle.copy(volume = 0.0) else candle }
        assertEquals(null, VolumeAnalysis.analyze(candles))
    }

    @Test
    fun volumeAnalysisIgnoresZeroVolumePriorSessions() {
        val candles = flatCandles(21).mapIndexed { index, candle ->
            when (index) { 0, 2, 4, 6, 8 -> candle.copy(volume = 0.0); 20 -> candle.copy(volume = 2000.0); else -> candle.copy(volume = 1000.0) }
        }
        val metrics = requireNotNull(VolumeAnalysis.analyze(candles))
        assertEquals(15, metrics.validPriorSessions)
        assertEquals(1000.0, metrics.averagePriorVolume, 0.001)
        assertEquals(2.0, metrics.relativeVolume, 0.001)
    }

    @Test
    fun missingCurrentVolumeDoesNotProduceRelativeVolume() {
        val candles = flatCandles(20) + flatCandles(1).single().copy(volume = 0.0)
        assertEquals(null, VolumeAnalysis.analyze(candles))
    }

    @Test
    fun missingVolumeIsNotTreatedAsConfirmedVolume() {
        val missing = OpportunityScoring.score(80, 80, null, 2.0)
        val average = OpportunityScoring.score(80, 80, 1.0, 2.0)
        assertTrue(missing != null && average != null)
        assertTrue(requireNotNull(missing) < requireNotNull(average))
    }

}


class PortfolioLedgerTest {
    @org.junit.Test
    fun weightedAverageIncludesBuyFeesAndSellFeeReducesProfit() {
        val result = requireNotNull(PortfolioLedger.calculate(listOf(
            LedgerTransaction(1, "فولاد", TransactionSide.BUY, 10, 100, 100),
            LedgerTransaction(2, "فولاد", TransactionSide.BUY, 10, 200, 100),
            LedgerTransaction(3, "فولاد", TransactionSide.SELL, 5, 200, 50)
        )))
        val position = result.positions.single()
        assertEquals(15L, position.quantity)
        assertEquals(160L, position.averageCostPerShare)
        assertEquals(150L, position.realizedProfitLoss)
        assertEquals(150L, result.realizedProfitLoss)
    }

    @org.junit.Test
    fun sellingMoreThanOwnedIsRejected() {
        assertEquals(null, PortfolioLedger.calculate(listOf(
            LedgerTransaction(1, "فولاد", TransactionSide.BUY, 5, 100),
            LedgerTransaction(2, "فولاد", TransactionSide.SELL, 6, 110)
        )))
    }

    @org.junit.Test
    fun ledgerNormalizesSymbolsAndRemovesFullyClosedPositions() {
        val result = requireNotNull(PortfolioLedger.calculate(listOf(
            LedgerTransaction(1, " فولاد ", TransactionSide.BUY, 5, 100),
            LedgerTransaction(2, "فولاد", TransactionSide.SELL, 5, 120)
        )))
        assertTrue(result.positions.isEmpty())
        assertEquals(100L, result.realizedProfitLoss)
    }

    @org.junit.Test
    fun invalidTransactionsAndOverflowAreRejected() {
        assertEquals(null, PortfolioLedger.calculate(listOf(
            LedgerTransaction(1, "فولاد", TransactionSide.BUY, 0, 100)
        )))
        assertEquals(null, PortfolioLedger.calculate(listOf(
            LedgerTransaction(1, "فولاد", TransactionSide.BUY, Long.MAX_VALUE, 2)
        )))
        assertEquals(null, PortfolioLedger.calculate(listOf(
            LedgerTransaction(1, "فولاد", TransactionSide.BUY, 2, 100),
            LedgerTransaction(2, "فولاد", TransactionSide.SELL, 1, 100, 101)
        )))
    }
}
