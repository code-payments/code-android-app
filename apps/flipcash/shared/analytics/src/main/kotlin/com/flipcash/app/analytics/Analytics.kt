package com.flipcash.app.analytics

import androidx.compose.runtime.Composable
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.libs.analytics.AnalyticsService
import com.getcode.libs.analytics.AppAction
import com.getcode.libs.analytics.AppActionSource
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.Mint

interface FlipcashAnalyticsService : AnalyticsService {
    fun transfer(
        event: AnalyticsEvent.Transfer,
        amount: LocalFiat? = null,
        grabTime: Long? = null,
        successful: Boolean = true,
        error: Throwable? = null
    )

    fun transfer(
        event: AnalyticsEvent.Transfer,
        fiat: Fiat? = null,
        grabTime: Long? = null,
        successful: Boolean = true,
        error: Throwable? = null
    )

    fun paidForAccount(
        price: Double,
        currency: CurrencyCode,
        owner: KeyPair,
    )

    fun openOnramp(
        openEvent: AnalyticsEvent.OnRampOpenEvent,
    )

    fun onrampVerification(
        verificationEvent: AnalyticsEvent.OnRampVerificationEvent,
    )

    fun onrampPurchase(
        purchaseEvent: AnalyticsEvent.OnRampPurchaseEvent,
        fiat: Fiat? = null,
        successful: Boolean = true,
        error: Throwable? = null
    )

    fun connectWallet(
        provider: OnRampProvider.UsesDeeplinks
    )

    fun amountSelectedForWalletTransfer(
        provider: OnRampProvider.UsesDeeplinks,
        amount: Fiat
    )

    fun transactionSubmittedToWallet(provider: OnRampProvider.UsesDeeplinks)
    fun walletTransactionFailed(provider: OnRampProvider.UsesDeeplinks)
    fun walletTransactionCancelled(provider: OnRampProvider.UsesDeeplinks)

    fun openTokenInfo(from: AnalyticsEvent.OpenTokenInfoEvent, mint: Mint)
    fun buy(
        method: AnalyticsEvent.TokenTransactionEvent.Purchase,
        amount: Fiat,
        mint: Mint,
        error: Throwable? = null
    )

    fun sell(
        amount: Fiat,
        feeAmount: Fiat,
        mint: Mint,
        error: Throwable? = null
    )
}

class StubFlipcashAnalytics : FlipcashAnalyticsService {
    override fun onAppStart() = Unit
    override fun onAppStarted() = Unit
    override fun unintentionalLogout() = Unit
    override fun action(action: AppAction, source: AppActionSource?) = Unit
    override fun transfer(
        event: AnalyticsEvent.Transfer,
        amount: LocalFiat?,
        grabTime: Long?,
        successful: Boolean,
        error: Throwable?
    ) = Unit

    override fun transfer(
        event: AnalyticsEvent.Transfer,
        fiat: Fiat?,
        grabTime: Long?,
        successful: Boolean,
        error: Throwable?
    ) = Unit

    override fun paidForAccount(price: Double, currency: CurrencyCode, owner: KeyPair) = Unit
    override fun openOnramp(openEvent: AnalyticsEvent.OnRampOpenEvent) = Unit
    override fun onrampVerification(verificationEvent: AnalyticsEvent.OnRampVerificationEvent) =
        Unit

    override fun onrampPurchase(
        purchaseEvent: AnalyticsEvent.OnRampPurchaseEvent,
        fiat: Fiat?,
        successful: Boolean,
        error: Throwable?
    ) = Unit

    override fun connectWallet(provider: OnRampProvider.UsesDeeplinks) = Unit
    override fun amountSelectedForWalletTransfer(
        provider: OnRampProvider.UsesDeeplinks,
        amount: Fiat
    ) = Unit

    override fun transactionSubmittedToWallet(provider: OnRampProvider.UsesDeeplinks) = Unit
    override fun walletTransactionFailed(provider: OnRampProvider.UsesDeeplinks) = Unit
    override fun walletTransactionCancelled(provider: OnRampProvider.UsesDeeplinks) = Unit

    override fun openTokenInfo(from: AnalyticsEvent.OpenTokenInfoEvent, mint: Mint) = Unit
    override fun buy(
        method: AnalyticsEvent.TokenTransactionEvent.Purchase,
        amount: Fiat,
        mint: Mint,
        error: Throwable?
    ) = Unit

    override fun sell(amount: Fiat, feeAmount: Fiat, mint: Mint, error: Throwable?) = Unit
}

@Composable
fun rememberAnalytics(): FlipcashAnalyticsService {
    return LocalAnalytics.current as FlipcashAnalyticsService
}