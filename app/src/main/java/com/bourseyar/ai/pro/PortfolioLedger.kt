package com.bourseyar.ai.pro

import java.math.BigDecimal
import java.math.RoundingMode

enum class TransactionSide { BUY, SELL }

data class LedgerTransaction(
    val timestamp: Long,
    val symbol: String,
    val side: TransactionSide,
    val quantity: Long,
    val unitPrice: Long,
    val fee: Long = 0L
)

data class LedgerPosition(
    val symbol: String,
    val quantity: Long,
    val averageCostPerShare: Long,
    val remainingCost: Long,
    val realizedProfitLoss: Long
)

data class LedgerResult(
    val positions: List<LedgerPosition>,
    val realizedProfitLoss: Long
)

/**
 * Replays a transaction ledger in timestamp order.
 * Buy fees are included in weighted average cost; sell fees reduce proceeds.
 * Invalid or oversold ledgers are rejected instead of silently producing a balance.
 */
object PortfolioLedger {
    fun calculate(transactions: List<LedgerTransaction>): LedgerResult? {
        if (transactions.any {
                it.symbol.isBlank() || it.quantity <= 0 || it.unitPrice <= 0 || it.fee < 0 ||
                    it.timestamp < 0 || runCatching { Math.multiplyExact(it.quantity, it.unitPrice) }.isFailure ||
                    (it.side == TransactionSide.SELL &&
                        (runCatching { Math.multiplyExact(it.quantity, it.unitPrice) }.getOrNull()?.let { gross -> it.fee > gross } ?: true))
            }) return null

        data class MutablePosition(
            var quantity: Long = 0,
            var remainingCost: Long = 0,
            var realized: Long = 0
        )

        val positions = linkedMapOf<String, MutablePosition>()
        val ordered = transactions.sortedWith(compareBy<LedgerTransaction> { it.timestamp })
        for (tx in ordered) {
            val key = tx.symbol.trim().uppercase(java.util.Locale.ROOT)
            val p = positions.getOrPut(key) { MutablePosition() }
            val gross = runCatching { Math.multiplyExact(tx.quantity, tx.unitPrice) }.getOrNull() ?: return null
            when (tx.side) {
                TransactionSide.BUY -> {
                    val newQuantity = runCatching { Math.addExact(p.quantity, tx.quantity) }.getOrNull() ?: return null
                    val addedCost = runCatching { Math.addExact(gross, tx.fee) }.getOrNull() ?: return null
                    p.remainingCost = runCatching { Math.addExact(p.remainingCost, addedCost) }.getOrNull() ?: return null
                    p.quantity = newQuantity
                }
                TransactionSide.SELL -> {
                    if (tx.quantity > p.quantity) return null
                    val costSold = BigDecimal.valueOf(p.remainingCost)
                        .multiply(BigDecimal.valueOf(tx.quantity))
                        .divide(BigDecimal.valueOf(p.quantity), 0, RoundingMode.HALF_UP)
                        .longValueExact()
                    val proceeds = runCatching { Math.subtractExact(gross, tx.fee) }.getOrNull() ?: return null
                    val realized = runCatching { Math.subtractExact(proceeds, costSold) }.getOrNull() ?: return null
                    p.remainingCost -= costSold
                    p.quantity -= tx.quantity
                    p.realized = runCatching { Math.addExact(p.realized, realized) }.getOrNull() ?: return null
                }
            }
        }
        val result = positions.map { (symbol, p) ->
            val average = if (p.quantity == 0L) 0L else
                BigDecimal.valueOf(p.remainingCost).divide(BigDecimal.valueOf(p.quantity), 0, RoundingMode.HALF_UP).longValueExact()
            LedgerPosition(symbol, p.quantity, average, p.remainingCost, p.realized)
        }
        val totalRealized = result.fold(0L) { sum, p -> runCatching { Math.addExact(sum, p.realizedProfitLoss) }.getOrNull() ?: return null }
        return LedgerResult(result.filter { it.quantity > 0 }, totalRealized)
    }
}
