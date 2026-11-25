package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortfolioLedgerTest {
    @Test
    fun calculatesWeightedAverageIncludingBuyFees() {
        val result = PortfolioLedger.calculate(
            listOf(
                LedgerTransaction(1, "فولاد", TransactionSide.BUY, 10, 100, 100),
                LedgerTransaction(2, "فولاد", TransactionSide.BUY, 10, 200, 100)
            )
        )

        val position = result!!.positions.single()
        assertEquals(20L, position.quantity)
        assertEquals(160L, position.averageCostPerShare)
        assertEquals(3_200L, position.remainingCost)
        assertEquals(0L, result.realizedProfitLoss)
    }

    @Test
    fun sellReducesRemainingCostAndIncludesSellFeeInRealizedProfit() {
        val result = PortfolioLedger.calculate(
            listOf(
                LedgerTransaction(1, "فولاد", TransactionSide.BUY, 10, 100, 100),
                LedgerTransaction(2, "فولاد", TransactionSide.SELL, 4, 150, 20)
            )
        )

        val position = result!!.positions.single()
        assertEquals(6L, position.quantity)
        assertEquals(660L, position.remainingCost)
        assertEquals(110L, position.averageCostPerShare)
        assertEquals(140L, result.realizedProfitLoss)
    }

    @Test
    fun sortsTransactionsByTimestampAndNormalizesSymbols() {
        val result = PortfolioLedger.calculate(
            listOf(
                LedgerTransaction(2, " فولاد ", TransactionSide.SELL, 2, 120),
                LedgerTransaction(1, "فولاد", TransactionSide.BUY, 5, 100)
            )
        )

        assertEquals(3L, result!!.positions.single().quantity)
        assertEquals(40L, result.realizedProfitLoss)
    }

    @Test
    fun removesFullySoldPositionsButKeepsRealizedProfit() {
        val result = PortfolioLedger.calculate(
            listOf(
                LedgerTransaction(1, "فولاد", TransactionSide.BUY, 5, 100),
                LedgerTransaction(2, "فولاد", TransactionSide.SELL, 5, 130)
            )
        )

        assertEquals(0, result!!.positions.size)
        assertEquals(150L, result.realizedProfitLoss)
    }

    @Test
    fun keepsSymbolsAsSeparatePositions() {
        val result = PortfolioLedger.calculate(
            listOf(
                LedgerTransaction(1, "فولاد", TransactionSide.BUY, 2, 100),
                LedgerTransaction(2, "فملی", TransactionSide.BUY, 3, 200)
            )
        )

        assertEquals(2, result!!.positions.size)
        assertEquals(5L, result.positions.sumOf { it.quantity })
    }

    @Test
    fun rejectsOversellingAndInvalidAmounts() {
        assertNull(
            PortfolioLedger.calculate(
                listOf(LedgerTransaction(1, "فولاد", TransactionSide.SELL, 1, 100))
            )
        )
        assertNull(
            PortfolioLedger.calculate(
                listOf(LedgerTransaction(1, "فولاد", TransactionSide.BUY, 0, 100))
            )
        )
        assertNull(
            PortfolioLedger.calculate(
                listOf(LedgerTransaction(1, "فولاد", TransactionSide.BUY, 1, 100, -1))
            )
        )
    }

    @Test
    fun rejectsArithmeticOverflowAndFeesAboveSellProceeds() {
        assertNull(
            PortfolioLedger.calculate(
                listOf(LedgerTransaction(1, "فولاد", TransactionSide.BUY, Long.MAX_VALUE, 2))
            )
        )
        assertNull(
            PortfolioLedger.calculate(
                listOf(
                    LedgerTransaction(1, "فولاد", TransactionSide.BUY, 1, 100),
                    LedgerTransaction(2, "فولاد", TransactionSide.SELL, 1, 100, 101)
                )
            )
        )
    }
}
