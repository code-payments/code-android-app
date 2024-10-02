package com.getcode.model

import com.codeinc.gen.transaction.v2.TransactionService
import com.codeinc.gen.transaction.v2.TransactionService.BuyModuleLimit
import com.codeinc.gen.transaction.v2.TransactionService.DepositLimit
import kotlin.time.Duration.Companion.hours

data class Limits(
    // Date from which the limits are computed
    val sinceDate : Long,

    // Date at which the limits were fetched
    val fetchDate: Long,

    // Maximum quarks that may be deposited at any time. Server will guarantee
    // this threshold will be below enforced dollar value limits, while also
    // ensuring sufficient funds are available for a full organizer that supports
    // max payment sends. Total dollar value limits may be spread across many deposits.
    val maxDeposit: Kin,

    // Remaining send limits keyed by currency
    private val sendLimits: Map<com.getcode.model.CurrencyCode, SendLimit>,
    // Buy limits keyed by currency
    private val buyLimits: Map<com.getcode.model.CurrencyCode, BuyLimit>,

    ) {
    val isStale: Boolean
        get() {
            val now = System.currentTimeMillis()
            return now - fetchDate > 1.hours.inWholeMilliseconds
        }

    fun sendLimitFor(currencyCode: com.getcode.model.CurrencyCode) : SendLimit? {
        return sendLimits[currencyCode]
    }

    fun buyLimitFor(currencyCode: com.getcode.model.CurrencyCode): BuyLimit? {
        return buyLimits[currencyCode]
    }

    companion object {
        fun newInstance(
            sinceDate: Long,
            fetchDate: Long,
            sendLimits: Map<String, TransactionService.SendLimit>,
            buyLimits: Map<String, BuyModuleLimit>,
            deposits: DepositLimit,
        ): Limits {
            val sends = sendLimits
                .mapNotNull { (k, v) ->
                    val code = com.getcode.model.CurrencyCode.tryValueOf(k) ?: return@mapNotNull null
                    val limit = SendLimit(
                        nextTransaction = v.nextTransaction.toDouble(),
                        maxPerDay = v.maxPerDay.toDouble(),
                        maxPerTransaction = v.maxPerTransaction.toDouble(),
                    )

                    code to limit
                }.toMap()

            val buys = buyLimits
                .mapValues { (_, v) ->
                    BuyLimit(
                        min = v.minPerTransaction.toDouble(),
                        max = v.maxPerTransaction.toDouble()
                    )
                }
                .mapNotNull { (k, limit) ->
                    val code = com.getcode.model.CurrencyCode.tryValueOf(k) ?: return@mapNotNull null
                    code to limit
                }.toMap()

            return Limits(
                sinceDate = sinceDate,
                fetchDate = fetchDate,
                sendLimits = sends,
                buyLimits = buys,
                maxDeposit = Kin.fromQuarks(deposits.maxQuarks)
            )
        }
    }
}

data class SendLimit(
    val nextTransaction: Double,
    val maxPerTransaction: Double,
    val maxPerDay: Double
) {
    companion object {
        val Zero = SendLimit(0.0, 0.0, 0.0)
    }
}

data class BuyLimit(val min: Double, val max: Double) {
    companion object {
        val Zero = BuyLimit(0.0, 0.0)
    }
}
