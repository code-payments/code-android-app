package com.getcode.opencode.model.financial

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.hours

data class Limits(
    // Date from which the limits are computed
    val sinceDate: Long,

    // Date at which the limits were fetched
    val fetchDate: Long,

    // Remaining send limits keyed by currency
    private val sendLimits: Map<CurrencyCode, SendLimit>,
    // Buy limits keyed by currency
    private val buyLimits: Map<CurrencyCode, BuyLimit>,

    // The amount of USD transacted since the consumption timestamp
    val amountUsdTransactedSinceConsumption: Fiat
) {
    val isStale: Boolean
        get() {
            val now = System.currentTimeMillis()
            return now - fetchDate > 1.hours.inWholeMilliseconds
        }

    fun sendLimitFor(currencyCode: CurrencyCode): SendLimit? {
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
            amountUsdTransactedSinceConsumption = Fiat.Zero
        )

        fun newInstance(
            sinceDate: Long,
            fetchDate: Long,
            sendLimits: Map<String, TransactionService.SendLimit>,
            buyLimits: Map<String, TransactionService.BuyModuleLimit>,
            usdTransactedSinceConsumption: Double
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
                amountUsdTransactedSinceConsumption = Fiat(usdTransactedSinceConsumption)
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
