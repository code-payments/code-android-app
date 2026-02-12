package com.getcode.opencode.internal.extensions

import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.solana.keys.Mint
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlin.time.Duration

fun VerifiedState.exchangeDataFor(
    amount: LocalFiat,
    mint: Mint,
    billExchangeDataTimeout: Duration?
): ExchangeData.Verified? {
    if (billExchangeDataTimeout == null) {
        trace(
            tag = "Transactor::Give",
            message = "No bill exchange data timeout provided. This bill is not giveable.",
            type = TraceType.Error
        )
        return null
    }
    return ExchangeData.Verified(
        mint = mint,
        nativeAmount = amount.nativeAmount.decimalValue,
        quarks = amount.underlyingTokenAmount.quarks,
        verifiedState = this,
    )
}