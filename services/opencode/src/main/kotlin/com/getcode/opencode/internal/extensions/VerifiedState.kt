package com.getcode.opencode.internal.extensions

import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.solana.keys.Mint
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val DefaultBillExchangeDataTimeout = 15.minutes

fun VerifiedState.exchangeDataFor(
    amount: LocalFiat,
    mint: Mint,
    billExchangeDataTimeout: Duration?
): ExchangeData.Verified? {
    val timeout = billExchangeDataTimeout ?: DefaultBillExchangeDataTimeout
    if (timeout <= Duration.ZERO) return null

    val ts = Instant.fromEpochSeconds(
        rateProto.exchangeRate.timestamp.seconds,
        rateProto.exchangeRate.timestamp.nanos
    )
    if (Clock.System.now() - ts > timeout) {
        return null
    }

    return ExchangeData.Verified(
        mint = mint,
        nativeAmount = amount.nativeAmount.decimalValue,
        quarks = amount.underlyingTokenAmount.quarks,
        verifiedState = this,
    )
}
