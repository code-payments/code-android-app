package com.flipcash.app.analytics.internal

import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.AnalyticsEvent
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.analytics.asProperties
import com.flipcash.app.analytics.toAnalyticsEvent
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.libs.analytics.AppAction
import com.getcode.libs.analytics.AppActionSource
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.services.flipcash.BuildConfig
import com.getcode.solana.keys.Mint
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.google.firebase.Firebase
import com.google.firebase.perf.metrics.Trace
import com.google.firebase.perf.performance
import com.mixpanel.android.mpmetrics.MixpanelAPI
import org.json.JSONObject
import javax.inject.Inject

internal class MixpanelAnalyticsDelegate @Inject constructor(
    private val mixpanelAPI: MixpanelAPI
) : FlipcashAnalyticsService {

    private var traceAppInit: Trace? = null
    private var timeAppInit: Long? = null

    override fun onAppStart() {
        timeAppInit = System.currentTimeMillis()
        traceAppInit = Firebase.performance.newTrace("Init")
        traceAppInit?.start()
    }

    override fun onAppStarted() {
        traceAppInit ?: return
        traceAppInit?.stop()
        traceAppInit = null
        val duration = System.currentTimeMillis() - (timeAppInit ?: 0)
        trace(
            tag = "Analytics",
            message = "App started",
            metadata = { "duration" to duration },
            type = TraceType.Process
        )
    }

    override fun unintentionalLogout() = Unit

    override fun action(action: AppAction, source: AppActionSource?) {
        track(name = action.value)
    }

    override fun transferStart(event: Analytics.Transfer.Initiate) {
        track(event.toAnalyticsEvent())
    }

    override fun transfer(
        event: Analytics.Transfer,
        amount: LocalFiat?,
        successful: Boolean,
        error: Throwable?
    ) {
        track(
            event.toAnalyticsEvent(),
            "State" to if (successful) "Success" else "Failure",
            *amount?.asProperties()?.toList()?.toTypedArray() ?: emptyArray(),
            *error.asProperty()
        )
    }

    override fun transfer(
        event: Analytics.Transfer,
        fiat: Fiat?,
        successful: Boolean,
        error: Throwable?
    ) {
        track(
            event.toAnalyticsEvent(),
            "State" to if (successful) "Success" else "Failure",
            *fiat?.asProperties()?.toList()?.toTypedArray() ?: emptyArray(),
            *error.asProperty()
        )
    }

    override fun paidForAccount(price: Double, currency: CurrencyCode, owner: KeyPair) {
        track(AnalyticsEvent.PaidForAccount(price, currency, owner))
    }

    override fun openOnramp(source: Analytics.OnrampSource) {
        val event = when (source) {
            Analytics.OnrampSource.Settings -> AnalyticsEvent.OnRampOpenEvent.Settings
            Analytics.OnrampSource.Balance -> AnalyticsEvent.OnRampOpenEvent.Balance
            Analytics.OnrampSource.Give -> AnalyticsEvent.OnRampOpenEvent.Give
        }
        track(event)
    }

    override fun onrampVerification(step: Analytics.OnrampVerificationStep) {
        val event = when (step) {
            Analytics.OnrampVerificationStep.ShowInfo -> AnalyticsEvent.OnRampVerificationEvent.ShowInfo
            Analytics.OnrampVerificationStep.EnterPhone -> AnalyticsEvent.OnRampVerificationEvent.EnterPhone
            Analytics.OnrampVerificationStep.ConfirmPhone -> AnalyticsEvent.OnRampVerificationEvent.ConfirmPhone
            Analytics.OnrampVerificationStep.EnterEmail -> AnalyticsEvent.OnRampVerificationEvent.EnterEmail
            Analytics.OnrampVerificationStep.ConfirmEmail -> AnalyticsEvent.OnRampVerificationEvent.ConfirmEmail
        }
        track(event)
    }

    override fun onrampPurchase(
        step: Analytics.OnrampPurchaseStep,
        amount: Fiat?
    ) {
        val event = when (step) {
            Analytics.OnrampPurchaseStep.PresetSelected -> AnalyticsEvent.OnRampPurchaseEvent.PresetSelected
            Analytics.OnrampPurchaseStep.EnterCustomAmount -> AnalyticsEvent.OnRampPurchaseEvent.EnterCustomAmount
            Analytics.OnrampPurchaseStep.InvokePayment -> amount?.let {
                AnalyticsEvent.OnRampPurchaseEvent.InvokePayment(
                    it
                )
            }

            Analytics.OnrampPurchaseStep.InvokePaymentCustom -> amount?.let {
                AnalyticsEvent.OnRampPurchaseEvent.InvokePaymentCustom(
                    it
                )
            }

            Analytics.OnrampPurchaseStep.Completed -> amount?.let {
                AnalyticsEvent.OnRampPurchaseEvent.Completed(
                    it
                )
            }
        } ?: return
        track(event)
    }

    override fun addMoneyOpened(source: Analytics.AddMoneySource) {
        track(AnalyticsEvent.AddMoneyEvent.Opened(source))
    }

    override fun addMoneyMethodSelected(method: Analytics.AddMoneyMethod) {
        track(AnalyticsEvent.AddMoneyEvent.MethodSelected(method))
    }

    override fun addMoneyAmountConfirmed(method: Analytics.AddMoneyMethod, amount: Fiat) {
        track(AnalyticsEvent.AddMoneyEvent.AmountConfirmed(method, amount))
    }

    override fun addMoneyPaymentInvoked(method: Analytics.AddMoneyMethod, amount: Fiat) {
        track(AnalyticsEvent.AddMoneyEvent.PaymentInvoked(method, amount))
    }

    override fun addMoneyAddressCopied(mint: Mint) {
        track(AnalyticsEvent.AddMoneyEvent.AddressCopied(mint))
    }

    override fun addMoney(
        method: Analytics.AddMoneyMethod,
        amount: Fiat?,
        successful: Boolean,
        error: Throwable?
    ) {
        track(
            AnalyticsEvent.AddMoneyEvent.Terminal(method),
            "State" to if (successful) "Success" else "Failure",
            *amount?.asProperties()?.toList()?.toTypedArray() ?: emptyArray(),
            *error.asProperty()
        )
    }

    override fun connectWallet(provider: OnRampProvider.UsesDeeplinks) {
        track(AnalyticsEvent.WalletConnect(provider))
    }

    override fun amountSelectedForWalletTransfer(
        provider: OnRampProvider.UsesDeeplinks,
        amount: Fiat
    ) {
        track(AnalyticsEvent.WalletRequestAmount(provider, amount))
    }

    override fun transactionSubmittedToWallet(provider: OnRampProvider.UsesDeeplinks) {
        track(AnalyticsEvent.WalletSubmitTransaction(provider))
    }

    override fun walletTransactionFailed(provider: OnRampProvider.UsesDeeplinks) {
        track(AnalyticsEvent.WalletTransactionFailed(provider))
    }

    override fun walletTransactionCancelled(provider: OnRampProvider.UsesDeeplinks) {
        track(AnalyticsEvent.WalletTransactionCancelled(provider))
    }

    override fun openTokenInfo(source: Analytics.TokenInfoSource, mint: Mint) {
        val event = when (source) {
            Analytics.TokenInfoSource.Deeplink -> AnalyticsEvent.OpenTokenInfoEvent.Deeplink(mint)
            Analytics.TokenInfoSource.Wallet -> AnalyticsEvent.OpenTokenInfoEvent.Wallet(mint)
            Analytics.TokenInfoSource.Give -> AnalyticsEvent.OpenTokenInfoEvent.Give(mint)
        }
        track(event)
    }

    override fun buy(
        method: Analytics.PurchaseMethod,
        mint: Mint,
        amount: Fiat,
        error: Throwable?
    ) {
        val event = when (method) {
            Analytics.PurchaseMethod.Reserves -> AnalyticsEvent.TokenTransactionEvent.Purchase.Reserves(
                mint,
                amount,
                error
            )

            Analytics.PurchaseMethod.Phantom -> AnalyticsEvent.TokenTransactionEvent.Purchase.Phantom(
                mint,
                amount,
                error
            )

            Analytics.PurchaseMethod.Coinbase -> AnalyticsEvent.TokenTransactionEvent.Purchase.Coinbase(
                mint,
                amount,
                error
            )
        }
        track(event)
    }

    override fun sell(mint: Mint, amount: Fiat, feeAmount: Fiat, error: Throwable?) {
        track(AnalyticsEvent.TokenTransactionEvent.Sell(mint, amount, feeAmount, error))
    }

    override fun messageSentInChat(error: Throwable?) {
        track(AnalyticsEvent.ChatEvent.SentMessage(error = error))
    }

    override fun deeplinkOpened(url: String) {
        track(AnalyticsEvent.DeeplinkEvent.Open(url))
    }

    override fun deeplinkParsed(type: DeeplinkType?, url: String) {
        track(AnalyticsEvent.DeeplinkEvent.Parse(type, url))
    }

    override fun deeplinkRouted(type: DeeplinkType, error: Throwable?) {
        track(AnalyticsEvent.DeeplinkEvent.Routed(type, error))
    }

    override fun displayedErrorModal(title: String, message: String, screen: String?, callSite: String?) {
        track(AnalyticsEvent.ErrorModalDisplayed(title, message, screen, callSite))
    }

    // region Internal

    private fun track(event: AnalyticsEvent, vararg extra: Pair<String, String>) {
        val properties = (event.toProperties().toList() + extra.toList()).toTypedArray()
        track(event.name, *properties)
    }

    private fun track(name: String, vararg properties: Pair<String, String>) {
        if (BuildConfig.DEBUG) {
            val propsString = properties.joinToString { "${it.first} => ${it.second}" }
            trace(
                buildString {
                    append("debug track $name")
                    if (propsString.isNotEmpty()) append(", $propsString")
                },
                type = TraceType.Silent
            )
            return
        }

        val jsonObject = JSONObject()
        properties.forEach { jsonObject.put(it.first, it.second) }
        mixpanelAPI.track(name, jsonObject)
    }

    private fun Throwable?.asProperty(): Array<Pair<String, String>> =
        this?.let { arrayOf("Error" to it.message.orEmpty()) } ?: emptyArray()
    // endregion
}