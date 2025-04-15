package com.getcode.opencode.model.financial

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import kotlinx.datetime.Instant
import kotlin.math.sin
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
    val maxDeposit: Fiat,

    // Remaining send limits keyed by currency
    private val sendLimits: Map<CurrencyCode, SendLimit>,
    // Buy limits keyed by currency
    private val buyLimits: Map<CurrencyCode, BuyLimit>,

    ) {
    val isStale: Boolean
        get() {
            val now = System.currentTimeMillis()
            return now - fetchDate > 1.hours.inWholeMilliseconds
        }

    fun sendLimitFor(currencyCode: CurrencyCode) : SendLimit? {
        return sendLimits[currencyCode]
    }

    fun buyLimitFor(currencyCode: CurrencyCode): BuyLimit? {
        return buyLimits[currencyCode]
    }

    companion object {
        val Empty = Limits(
            sinceDate = Instant.DISTANT_PAST.toEpochMilliseconds(),
            fetchDate = Instant.DISTANT_PAST.toEpochMilliseconds(),
            sendLimits = emptyMap(),
            buyLimits = emptyMap(),
            maxDeposit = Fiat.Zero
        )

        fun newInstance(
            sinceDate: Long,
            fetchDate: Long,
            sendLimits: Map<String, TransactionService.SendLimit>,
            buyLimits: Map<String, TransactionService.BuyModuleLimit>,
            deposits: TransactionService.DepositLimit,
        ): Limits {
            val sends = sendLimits
                .mapNotNull { (k, v) ->
                    val code = CurrencyCode.tryValueOf(k) ?: return@mapNotNull null
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
                    val code = CurrencyCode.tryValueOf(k) ?: return@mapNotNull null
                    code to limit
                }.toMap()

            return Limits(
                sinceDate = sinceDate,
                fetchDate = fetchDate,
                sendLimits = sends,
                buyLimits = buys,
                maxDeposit = Fiat(quarks = deposits.maxQuarks.toULong(), currencyCode = CurrencyCode.USD)
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
