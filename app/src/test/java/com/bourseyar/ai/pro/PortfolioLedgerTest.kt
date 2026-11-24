package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortfolioLedgerTest {
    @Test
    fun calculatesWeightedAverageCostAndRealizedProfitAfterFees() {
        val result = PortfolioLedger.calculate(
            listOf(
                LedgerTransaction(1, "فولاد", TransactionSide.BUY, 10, 100, 10),
                LedgerTransaction(2, "فولاد", TransactionSide.BUY, 10, 200, 10),
                LedgerTransaction(3, "فولاد", TransactionSide.SELL, 5, 250, 5)
            )
        )

        val position = result!!.positions.single()
        assertEquals(15L, position.quantity)
        assertEquals(152L, position.averageCostPerShare)
        assertEquals(2_280L, position.remainingCost)
        assertEquals(485L, position.realizedProfitLoss)
        assertEquals(485L, result.realizedProfitLoss)
    }

    @Test
    fun replaysTransactionsChronologicallyRegardlessOfInputOrder() {
        val buy = LedgerTransaction(1, "فولاد", TransactionSide.BUY, 2, 100)
        val sell = LedgerTransaction(2, "فولاد", TransactionSide.SELL, 1, 120)
        val result = PortfolioLedger.calculate(listOf(sell, buy))
        assertEquals(1L, result!!.positions.single().quantity)
        assertEquals(20L, result.realizedProfitLoss)
    }

    @Test
    fun rejectsOversellingAndInvalidTransactions() {
        assertNull(PortfolioLedger.calculate(listOf(
            LedgerTransaction(1, "فولاد", TransactionSide.SELL, 1, 100)
        )))
        assertNull(PortfolioLedger.calculate(listOf(
            LedgerTransaction(1, "فولاد", TransactionSide.BUY, 0, 100)
        )))
    }
}
