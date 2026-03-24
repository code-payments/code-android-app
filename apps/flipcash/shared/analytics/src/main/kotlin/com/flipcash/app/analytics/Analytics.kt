package com.flipcash.app.analytics

import androidx.compose.runtime.Composable
import com.flipcash.app.core.navigation.DeeplinkType
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
    fun transferStart(event: Analytics.Transfer.Initiate)
    fun transfer(event: Analytics.Transfer, amount: LocalFiat?, successful: Boolean = true, error: Throwable? = null)
    fun transfer(event: Analytics.Transfer, fiat: Fiat?, successful: Boolean = true, error: Throwable? = null)
    fun paidForAccount(price: Double, currency: CurrencyCode, owner: KeyPair)
    fun openOnramp(source: Analytics.OnrampSource)
    fun onrampVerification(step: Analytics.OnrampVerificationStep)
    fun onrampPurchase(step: Analytics.OnrampPurchaseStep, amount: Fiat? = null)
    fun connectWallet(provider: OnRampProvider.UsesDeeplinks)
    fun amountSelectedForWalletTransfer(provider: OnRampProvider.UsesDeeplinks, amount: Fiat)
    fun transactionSubmittedToWallet(provider: OnRampProvider.UsesDeeplinks)
    fun walletTransactionFailed(provider: OnRampProvider.UsesDeeplinks)
    fun walletTransactionCancelled(provider: OnRampProvider.UsesDeeplinks)
    fun openTokenInfo(source: Analytics.TokenInfoSource, mint: Mint)
    fun buy(method: Analytics.PurchaseMethod, mint: Mint, amount: Fiat, error: Throwable? = null)
    fun sell(mint: Mint, amount: Fiat, feeAmount: Fiat, error: Throwable? = null)
    fun deeplinkOpened(url: String)
    fun deeplinkParsed(type: DeeplinkType?, url: String)
    fun deeplinkRouted(type: DeeplinkType, error: Throwable? = null)

    fun buttonTapped(button: Button) {
        action(button)
    }
}

object Analytics {

    sealed interface Transfer {
        sealed interface Initiate: Transfer {
            data object GrabBillStart: Initiate
            data object GiveBillStart: Initiate
        }

        data class GrabBill(val time: Long? = null) : Transfer
        data object GiveBill : Transfer
        data object Withdrawal : Transfer
        data object ClaimedCashLink : Transfer
        sealed interface SentCashLink : Transfer {
            data object Clipboard : SentCashLink
            data class App(val name: String) : SentCashLink
        }
    }
    enum class OnrampSource { Settings, Balance, Give }
    enum class OnrampVerificationStep { ShowInfo, EnterPhone, ConfirmPhone, EnterEmail, ConfirmEmail }
    enum class OnrampPurchaseStep { PresetSelected, EnterCustomAmount, InvokePayment, InvokePaymentCustom, Completed }
    enum class TokenInfoSource { Deeplink, Wallet, Give }
    enum class PurchaseMethod { Reserves, Phantom, Coinbase }
    sealed interface SwapMethod {
        enum class Buy(val with: PurchaseMethod) : SwapMethod {
            Reserves(PurchaseMethod.Reserves),
            Phantom(PurchaseMethod.Phantom),
            Coinbase(PurchaseMethod.Coinbase)
        }
        data object Sell : SwapMethod
    }
}

class StubFlipcashAnalytics : FlipcashAnalyticsService {
    override fun onAppStart() = Unit
    override fun onAppStarted() = Unit
    override fun unintentionalLogout() = Unit
    override fun action(action: AppAction, source: AppActionSource?) = Unit

    override fun transferStart(event: Analytics.Transfer.Initiate) = Unit
    override fun transfer(event: Analytics.Transfer, amount: LocalFiat?, successful: Boolean, error: Throwable?) = Unit
    override fun transfer(event: Analytics.Transfer, fiat: Fiat?, successful: Boolean, error: Throwable?) = Unit
    override fun paidForAccount(price: Double, currency: CurrencyCode, owner: KeyPair) = Unit

    override fun openOnramp(source: Analytics.OnrampSource) = Unit
    override fun onrampVerification(step: Analytics.OnrampVerificationStep) = Unit
    override fun onrampPurchase(step: Analytics.OnrampPurchaseStep, amount: Fiat?) = Unit

    override fun connectWallet(provider: OnRampProvider.UsesDeeplinks) = Unit
    override fun amountSelectedForWalletTransfer(provider: OnRampProvider.UsesDeeplinks, amount: Fiat) = Unit
    override fun transactionSubmittedToWallet(provider: OnRampProvider.UsesDeeplinks) = Unit
    override fun walletTransactionFailed(provider: OnRampProvider.UsesDeeplinks) = Unit
    override fun walletTransactionCancelled(provider: OnRampProvider.UsesDeeplinks) = Unit

    override fun openTokenInfo(source: Analytics.TokenInfoSource, mint: Mint) = Unit
    override fun buy(method: Analytics.PurchaseMethod, mint: Mint, amount: Fiat, error: Throwable?) = Unit
    override fun sell(mint: Mint, amount: Fiat, feeAmount: Fiat, error: Throwable?) = Unit

    override fun deeplinkOpened(url: String) = Unit
    override fun deeplinkParsed(type: DeeplinkType?, url: String) = Unit
    override fun deeplinkRouted(type: DeeplinkType, error: Throwable?) = Unit
}

@Composable
fun rememberAnalytics(): FlipcashAnalyticsService {
    return LocalAnalytics.current as FlipcashAnalyticsService
}