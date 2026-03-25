package com.getcode.opencode.internal.extensions

import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.solana.keys.Mint
import kotlin.time.Duration

fun VerifiedState.exchangeDataFor(
    amount: LocalFiat,
    mint: Mint,
    billExchangeDataTimeout: Duration?
): ExchangeData.Verified? {
    if (billExchangeDataTimeout == null) {
        return null
    }
    return ExchangeData.Verified(
        mint = mint,
        nativeAmount = amount.nativeAmount.decimalValue,
        quarks = amount.underlyingTokenAmount.quarks,
        verifiedState = this,
    )
}
