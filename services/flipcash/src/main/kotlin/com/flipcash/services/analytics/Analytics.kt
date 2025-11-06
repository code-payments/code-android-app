package com.flipcash.services.analytics

import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.libs.analytics.AnalyticsService
import com.getcode.libs.analytics.AppAction
import com.getcode.libs.analytics.AppActionSource
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.services.flipcash.BuildConfig
import com.getcode.solana.keys.base58
import com.getcode.utils.TraceType
import com.getcode.utils.base58
import com.getcode.utils.getPublicKeyBase58
import com.getcode.utils.trace
import com.google.firebase.Firebase
import com.google.firebase.perf.metrics.Trace
import com.google.firebase.perf.performance
import com.mixpanel.android.mpmetrics.MixpanelAPI
import org.json.JSONObject
import javax.inject.Inject

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

    fun poolOpenedFromDeeplink(id: ID)
    fun poolCreated(id: ID)
    fun placedBidInPool(id: ID)
    fun declaredOutcomeInPool(id: ID)

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
}

class FlipcashAnalyticsManager @Inject constructor(
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
            metadata = {
                "duration" to duration
            },
            type = TraceType.Process
        )
    }

    override fun unintentionalLogout() = Unit

    override fun action(action: AppAction, source: AppActionSource?) {
        track(name = action.value)
    }

    override fun transfer(
        event: AnalyticsEvent.Transfer,
        amount: LocalFiat?,
        grabTime: Long?,
        successful: Boolean,
        error: Throwable?
    ) {
        val properties =
            event.properties(localizedAmount = amount, successful = successful, error = error)
        track(event.name, *properties.toList().toTypedArray())
    }

    override fun transfer(
        event: AnalyticsEvent.Transfer,
        fiat: Fiat?,
        grabTime: Long?,
        successful: Boolean,
        error: Throwable?
    ) {
        val properties =
            event.properties(nativeAmount = fiat, successful = successful, error = error)
        track(event.name, *properties.toList().toTypedArray())
    }

    override fun paidForAccount(price: Double, currency: CurrencyCode, owner: KeyPair) {
        val event = AnalyticsEvent.PaidForAccount(price, currency, owner)
        val properties = event.properties()
        track(event.name, *properties.toList().toTypedArray())
    }

    override fun poolOpenedFromDeeplink(id: ID) {
        val event = AnalyticsEvent.PoolOpened(id)
        val properties = event.properties()
        track(event.name, *properties.toList().toTypedArray())
    }

    override fun poolCreated(id: ID) {
        val event = AnalyticsEvent.PoolCreated(id)
        val properties = event.properties()
        track(event.name, *properties.toList().toTypedArray())
    }

    override fun placedBidInPool(id: ID) {
        val event = AnalyticsEvent.PlacedBid(id)
        val properties = event.properties()
        track(event.name, *properties.toList().toTypedArray())
    }

    override fun declaredOutcomeInPool(id: ID) {
        val event = AnalyticsEvent.DeclaredOutcome(id)
        val properties = event.properties()
        track(event.name, *properties.toList().toTypedArray())
    }

    override fun openOnramp(openEvent: AnalyticsEvent.OnRampOpenEvent) {
        val properties = openEvent.properties()
        track(openEvent.name, *properties.toList().toTypedArray())
    }

    override fun onrampVerification(verificationEvent: AnalyticsEvent.OnRampVerificationEvent) {
        val properties = verificationEvent.properties()
        track(verificationEvent.name, *properties.toList().toTypedArray())
    }

    override fun onrampPurchase(
        purchaseEvent: AnalyticsEvent.OnRampPurchaseEvent,
        fiat: Fiat?,
        successful: Boolean,
        error: Throwable?
    ) {
        val properties =
            purchaseEvent.properties(nativeAmount = fiat, successful = successful, error = error)
        track(purchaseEvent.name, *properties.toList().toTypedArray())
    }

    override fun connectWallet(provider: OnRampProvider.UsesDeeplinks) {
        val event = AnalyticsEvent.WalletConnect(provider)
        val properties = event.properties()
        track(event.name, *properties.toList().toTypedArray())
    }

    override fun amountSelectedForWalletTransfer(
        provider: OnRampProvider.UsesDeeplinks,
        amount: Fiat
    ) {
        val event = AnalyticsEvent.WalletRequestAmount(provider)
        val properties = event.properties(
            nativeAmount = amount
        )
        track(event.name, *properties.toList().toTypedArray())
    }

    override fun transactionSubmittedToWallet(provider: OnRampProvider.UsesDeeplinks) {
        val event = AnalyticsEvent.WalletSubmitTransaction(provider)
        val properties = event.properties()
        track(event.name, *properties.toList().toTypedArray())
    }

    override fun walletTransactionFailed(provider: OnRampProvider.UsesDeeplinks) {
        val event = AnalyticsEvent.WalletTransactionFailed(provider)
        val properties = event.properties()
        track(event.name, *properties.toList().toTypedArray())
    }

    override fun walletTransactionCancelled(provider: OnRampProvider.UsesDeeplinks) {
        val event = AnalyticsEvent.WalletTransactionCancelled(provider)
        val properties = event.properties()
        track(event.name, *properties.toList().toTypedArray())
    }

    private fun track(name: String, vararg properties: Pair<String, String>) {
        if (BuildConfig.DEBUG) {
            trace(
                "debug track ${name}, ${properties.joinToString { "${it.first} => ${it.second}" }}",
                type = TraceType.Silent
            )
            return
        } //no logging in debug

        val jsonObject = JSONObject()
        properties.forEach { property ->
            jsonObject.put(property.first, property.second)
        }
        mixpanelAPI.track(name, jsonObject)
    }
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
    override fun poolOpenedFromDeeplink(id: ID) = Unit
    override fun poolCreated(id: ID) = Unit
    override fun placedBidInPool(id: ID) = Unit
    override fun declaredOutcomeInPool(id: ID) = Unit
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
}

sealed class Action : AppAction {
    data object CreateAccount : Action() {
        override val value: String = "Button: Create Account"
    }

    data object SaveAccessKey : Action() {
        override val value: String = "Button: Save Access Key"
    }

    data object WroteAccessKey : Action() {
        override val value: String = "Button: Wrote Access Key"
    }

    data object AllowCamera : Action() {
        override val value: String = "Button: Allow Camera"
    }

    data object AllowPush : Action() {
        override val value: String = "Button: Allow Push"
    }

    data object SkipPush : Action() {
        override val value: String = "Button: Skip Push"
    }

    data object CompletedOnboarding : Action() {
        override val value: String = "Complete Onboarding"
    }
}

sealed interface AnalyticsEvent {

    val name: String

    data class PaidForAccount(
        val price: Double,
        val currency: CurrencyCode,
        val owner: KeyPair
    ) : AnalyticsEvent {
        override val name: String = "Create Account Payment"
    }

    sealed interface Transfer : AnalyticsEvent
    data object GrabBill : Transfer {
        override val name: String = "Grab Bill"
    }

    data object GiveBill : Transfer {
        override val name: String = "Give Bill"
    }

    data object Withdrawal : Transfer {
        override val name: String = "Withdrawal"
    }

    data class SentCashLink(
        val clipboard: Boolean? = null,
        val app: String? = null
    ) : Transfer {
        override val name: String = "Send Cash Link"
    }

    data object ClaimedCashLink : Transfer {
        override val name: String = "Receive Cash Link"
    }

    sealed interface PoolEvent : AnalyticsEvent {
        val id: ID
    }

    data class PoolOpened(override val id: ID) : PoolEvent {
        override val name: String = "Pool: Opened From Deeplink"
    }

    data class PoolCreated(override val id: ID) : PoolEvent {
        override val name: String = "Pool: Created"
    }

    data class PlacedBid(override val id: ID) : PoolEvent {
        override val name: String = "Pool: Place Bet"
    }

    data class DeclaredOutcome(override val id: ID) : PoolEvent {
        override val name: String = "Pool: Declared Outcome"
    }

    sealed interface OnRampOpenEvent : AnalyticsEvent {
        data object Settings : OnRampOpenEvent {
            override val name: String = "Onramp: Opened From Settings"
        }

        data object Balance : OnRampOpenEvent {
            override val name: String = "Onramp: Opened From Balance"
        }

        data object Give : OnRampOpenEvent {
            override val name: String = "Onramp: Opened From Give"
        }
    }

    sealed interface OnRampVerificationEvent : AnalyticsEvent {
        data object ShowInfo : OnRampVerificationEvent {
            override val name: String = "Onramp: Show Verification Info"
        }

        data object EnterPhone : OnRampVerificationEvent {
            override val name: String = "Onramp: Show Enter Phone"
        }

        data object ConfirmPhone : OnRampVerificationEvent {
            override val name: String = "Onramp: Show Confirm Phone"
        }

        data object EnterEmail : OnRampVerificationEvent {
            override val name: String = "Onramp: Show Enter Email"
        }

        data object ConfirmEmail : OnRampVerificationEvent {
            override val name: String = "Onramp: Show Confirm Email"
        }
    }

    sealed interface OnRampPurchaseEvent : AnalyticsEvent {
        data object PresetSelected : OnRampPurchaseEvent {
            override val name: String = "Onramp: Amount Selected"
        }

        data object EnterCustomAmount : OnRampPurchaseEvent {
            override val name: String = "Onramp: Enter Custom Amount"
        }

        data object InvokePayment : OnRampPurchaseEvent {
            override val name: String = "Onramp: Invoke Payment"
        }

        data object InvokePaymentCustom : OnRampPurchaseEvent {
            override val name: String = "Onramp: Invoke Payment Custom"
        }

        data object Completed : OnRampPurchaseEvent {
            override val name: String = "Onramp: Completed"
        }
    }

    sealed interface WalletEvent : AnalyticsEvent {
        val provider: OnRampProvider.UsesDeeplinks
    }

    data class WalletConnect(override val provider: OnRampProvider.UsesDeeplinks) : WalletEvent {
        override val name: String = "Wallet: Connect"
    }

    data class WalletRequestAmount(override val provider: OnRampProvider.UsesDeeplinks) :
        WalletEvent {
        override val name: String = "Wallet: Request Amount"
    }

    data class WalletSubmitTransaction(override val provider: OnRampProvider.UsesDeeplinks) :
        WalletEvent {
        override val name: String = "Wallet: Transactions Submitted"
    }

    data class WalletTransactionFailed(override val provider: OnRampProvider.UsesDeeplinks) :
        WalletEvent {
        override val name: String = "Wallet: Transactions Failed"
    }

    data class WalletTransactionCancelled(override val provider: OnRampProvider.UsesDeeplinks) :
        WalletEvent {
        override val name: String = "Wallet: Cancel"
    }
}

private fun AnalyticsEvent.properties(
    localizedAmount: LocalFiat? = null,
    nativeAmount: Fiat? = null,
    grabTime: Long? = null,
    successful: Boolean? = null,
    error: Throwable? = null,
): Map<String, String> {
    return buildMap {
        if (successful != null) {
            if (successful) {
                put("State", "Success")
            } else {
                put("State", "Failure")
            }
        }

        val providerName = if (this@properties is AnalyticsEvent.WalletEvent) {
            when (provider) {
                OnRampProvider.Backpack -> "Backpack"
                OnRampProvider.Solflare -> "Solflare"
                OnRampProvider.Phantom -> "Phantom"
            }
        } else {
            ""
        }

        when (val event = this@properties) {
            is AnalyticsEvent.SentCashLink -> {
                if (event.clipboard == true) {
                    put("Cash Link Choice", "Copied to clipboard")
                } else if (event.app != null) {
                    put("Cash Link Choice", "Shared to app")
                    put("App", event.app)
                }
            }

            AnalyticsEvent.Withdrawal,
            AnalyticsEvent.ClaimedCashLink,
            AnalyticsEvent.GiveBill,
            AnalyticsEvent.GrabBill -> Unit

            is AnalyticsEvent.PaidForAccount -> {
                put("Fiat", event.price.toString())
                put("Currency", event.currency.name)
                put("Owner Public Key", event.owner.getPublicKeyBase58())
            }

            is AnalyticsEvent.PoolEvent -> {
                put("ID", event.id.base58)
            }

            is AnalyticsEvent.WalletConnect -> {
                put("Provider", providerName)
            }

            is AnalyticsEvent.WalletRequestAmount -> {
                put("Provider", providerName)
                put("Fiat", nativeAmount?.decimalValue.toString())
                put("Currency", nativeAmount?.currencyCode?.name.orEmpty())
            }

            is AnalyticsEvent.WalletSubmitTransaction -> {
                put("Provider", providerName)
            }

            is AnalyticsEvent.WalletTransactionCancelled -> {
                put("Provider", providerName)
            }

            is AnalyticsEvent.WalletTransactionFailed -> {
                put("Provider", providerName)
            }

            is AnalyticsEvent.OnRampOpenEvent -> Unit
            is AnalyticsEvent.OnRampVerificationEvent -> Unit
            AnalyticsEvent.OnRampPurchaseEvent.Completed -> {
                put("Fiat", nativeAmount?.decimalValue.toString())
                put("Currency", nativeAmount?.currencyCode?.name.orEmpty())
            }

            AnalyticsEvent.OnRampPurchaseEvent.EnterCustomAmount -> Unit
            AnalyticsEvent.OnRampPurchaseEvent.InvokePayment -> {
                put("Fiat", nativeAmount?.decimalValue.toString())
                put("Currency", nativeAmount?.currencyCode?.name.orEmpty())
            }

            AnalyticsEvent.OnRampPurchaseEvent.InvokePaymentCustom -> {
                put("Fiat", nativeAmount?.decimalValue.toString())
                put("Currency", nativeAmount?.currencyCode?.name.orEmpty())
            }

            AnalyticsEvent.OnRampPurchaseEvent.PresetSelected -> {
                put("Fiat", nativeAmount?.decimalValue.toString())
                put("Currency", nativeAmount?.currencyCode?.name.orEmpty())
            }
        }

        if (localizedAmount != null) {
            putAll(localizedAmount.asProperties())
        } else if (nativeAmount != null) {
            putAll(nativeAmount.asProperties())
        }

        if (grabTime != null) {
            put("Grab Time", grabTime.toString())
        }

        if (error != null) {
            put("Error", error.message.orEmpty())
        }
    }
}

private fun LocalFiat.asProperties(): Map<String, String> {
    return buildMap {
        putAll(underlyingTokenAmount.asProperties())
        "Fiat" to nativeAmount.decimalValue.toString()
        "Exchange Rate" to rate.fx.toString()
        "Currency" to rate.currency.name
        "Mint" to mint.base58()
    }
}

private fun Fiat.asProperties(): Map<String, String> {
    return buildMap {
        "USDC" to decimalValue.toString()
        "Quarks" to quarks.toDouble().toString()
    }
}