package com.getcode.opencode.exchange

import android.os.Parcelable
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Parcelize
@Serializable
data class VerifiedFiat(
    val localFiat: LocalFiat,
    @Transient
    @IgnoredOnParcel
    val verifiedState: VerifiedState? = null,
) : Parcelable

interface VerifiedFiatCalculator {
    suspend fun resolveVerifiedState(
        currencyCode: CurrencyCode,
        mint: Mint
    ): VerifiedState?

    suspend fun compute(
        amount: Fiat,
        token: Token,
        balance: Fiat? = null,
        rate: Rate,
        trace: Boolean = true,
    ): Result<VerifiedFiat>
}
