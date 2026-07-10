package com.flipcash.app.analytics

import androidx.core.net.toUri
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.utils.base58
import com.getcode.utils.getPublicKeyBase58

internal sealed interface AnalyticsEvent {
    val name: String
    fun toProperties(): Map<String, String> = emptyMap()

    data class PaidForAccount(
        val price: Double,
        val currency: CurrencyCode,
        val owner: KeyPair
    ) : AnalyticsEvent {
        override val name = "Create Account Payment"
        override fun toProperties() = mapOf(
            "Fiat" to price.toString(),
            "Currency" to currency.name,
            "Owner Public Key" to owner.getPublicKeyBase58()
        )
    }

    data class ErrorModalDisplayed(
        val title: String,
        val message: String,
        val screen: String? = null,
        val callSite: String? = null,
    ): AnalyticsEvent {
        override val name = "Error Modal Displayed"
        override fun toProperties() = buildMap {
            put("Title", title)
            put("Message", message)
            screen?.let { put("Screen", it) }
            callSite?.let { put("Call Site", it) }
        }
    }

    sealed interface DeeplinkEvent : AnalyticsEvent {
        data class Open(val url: String) : DeeplinkEvent {
            override val name = "Deeplink: Open"
            override fun toProperties() = mapOf("URL" to url.sanitized())

            private fun String.sanitized(): String {
                val uri = this.toUri()
                return try {
                    uri.buildUpon()
                        .clearQuery()
                        .fragment(null)
                        .build()
                        .toString()
                } catch (_: Exception) {
                    uri.path ?: url
                }
            }
        }

        data class Parse(
            val type: DeeplinkType? = null,
            val url: String,
        ) : DeeplinkEvent {
            override val name = "Deeplink: Parse"
            override fun toProperties() = buildMap {
                type?.let { put("Type", it.javaClass.simpleName) }
                if (type == null) {
                    put("Error", "Failed to parse deeplink => $url")
                }
            }
        }

        data class Routed(
            val type: DeeplinkType,
            val error: Throwable? = null
        ) : DeeplinkEvent {
            override val name = "Deeplink: Routed"
            override fun toProperties() = buildMap {
                put("Type", type.javaClass.simpleName)
                error?.let { put("Error", it.message.orEmpty()) }
            }
        }
    }

    sealed interface Transfer : AnalyticsEvent

    data object GrabBillStart: Transfer {
        override val name = "Grab Bill Start"
    }

    data class GrabBill(val time: Long?) : Transfer {
        override val name = "Grab Bill"
        override fun toProperties() = buildMap {
            time?.let { put("Grab Time", it.toString()) }
        }
    }

    data object GiveBill : Transfer {
        override val name = "Give Bill"
    }

    data object GiveBillStart : Transfer {
        override val name = "Give Bill Start"
    }

    data object Withdrawal : Transfer {
        override val name = "Withdrawal"
    }

    data class SentCashLink(
        val clipboard: Boolean? = null,
        val app: String? = null
    ) : Transfer {
        override val name = "Send Cash Link"
        override fun toProperties() = buildMap {
            if (clipboard == true) {
                put("Cash Link Choice", "Copied to clipboard")
            } else if (app != null) {
                put("Cash Link Choice", "Shared to app")
                put("App", app)
            }
        }
    }

    data object ClaimedCashLink : Transfer {
        override val name = "Receive Cash Link"
    }

    sealed interface ChatEvent: AnalyticsEvent {
        data object SentCash : ChatEvent {
            override val name = "Sent Cash"
        }

        data class SentMessage(
            val error: Throwable? = null
        ): ChatEvent {
            override val name = "Sent Message"

            override fun toProperties() = buildMap {
                error?.let { put("Error", it.message.orEmpty()) }
            }
        }
    }

    sealed interface PoolEvent : AnalyticsEvent {
        val id: ID
        override fun toProperties() = mapOf("ID" to id.base58)
    }

    data class PoolOpened(override val id: ID) : PoolEvent {
        override val name = "Pool: Opened From Deeplink"
    }

    data class PoolCreated(override val id: ID) : PoolEvent {
        override val name = "Pool: Created"
    }

    data class PlacedBid(override val id: ID) : PoolEvent {
        override val name = "Pool: Place Bet"
    }

    data class DeclaredOutcome(override val id: ID) : PoolEvent {
        override val name = "Pool: Declared Outcome"
    }

    sealed interface WalletEvent : AnalyticsEvent {
        val provider: OnRampProvider.UsesDeeplinks
        val providerName get() = when (provider) {
            OnRampProvider.Phantom -> "Phantom"
        }
    }

    data class WalletConnect(override val provider: OnRampProvider.UsesDeeplinks) : WalletEvent {
        override val name = "Wallet: Connect"
        override fun toProperties() = mapOf("Provider" to providerName)
    }

    data class WalletRequestAmount(
        override val provider: OnRampProvider.UsesDeeplinks,
        val amount: Fiat
    ) : WalletEvent {
        override val name = "Wallet: Request Amount"
        override fun toProperties() = buildMap {
            put("Provider", providerName)
            putAll(amount.asProperties())
        }
    }

    data class WalletSubmitTransaction(override val provider: OnRampProvider.UsesDeeplinks) : WalletEvent {
        override val name = "Wallet: Transactions Submitted"
        override fun toProperties() = mapOf("Provider" to providerName)
    }

    data class WalletTransactionFailed(override val provider: OnRampProvider.UsesDeeplinks) : WalletEvent {
        override val name = "Wallet: Transactions Failed"
        override fun toProperties() = mapOf("Provider" to providerName)
    }

    data class WalletTransactionCancelled(override val provider: OnRampProvider.UsesDeeplinks) : WalletEvent {
        override val name = "Wallet: Cancel"
        override fun toProperties() = mapOf("Provider" to providerName)
    }

    sealed interface OnRampOpenEvent : AnalyticsEvent {
        data object Settings : OnRampOpenEvent { override val name = "Onramp: Opened From Settings" }
        data object Balance : OnRampOpenEvent { override val name = "Onramp: Opened From Balance" }
        data object Give : OnRampOpenEvent { override val name = "Onramp: Opened From Give" }
    }

    sealed interface OnRampVerificationEvent : AnalyticsEvent {
        data object ShowInfo : OnRampVerificationEvent { override val name = "Onramp: Show Verification Info" }
        data object EnterPhone : OnRampVerificationEvent { override val name = "Onramp: Show Enter Phone" }
        data object ConfirmPhone : OnRampVerificationEvent { override val name = "Onramp: Show Confirm Phone" }
        data object EnterEmail : OnRampVerificationEvent { override val name = "Onramp: Show Enter Email" }
        data object ConfirmEmail : OnRampVerificationEvent { override val name = "Onramp: Show Confirm Email" }
    }

    sealed interface OnRampPurchaseEvent : AnalyticsEvent {
        data object PresetSelected : OnRampPurchaseEvent { override val name = "Onramp: Amount Selected" }
        data object EnterCustomAmount : OnRampPurchaseEvent { override val name = "Onramp: Enter Custom Amount" }

        data class InvokePayment(val amount: Fiat) : OnRampPurchaseEvent {
            override val name = "Onramp: Invoke Payment"
            override fun toProperties() = amount.asProperties()
        }

        data class InvokePaymentCustom(val amount: Fiat) : OnRampPurchaseEvent {
            override val name = "Onramp: Invoke Payment Custom"
            override fun toProperties() = amount.asProperties()
        }

        data class Completed(val amount: Fiat) : OnRampPurchaseEvent {
            override val name = "Onramp: Completed"
            override fun toProperties() = amount.asProperties()
        }
    }

    sealed interface AddMoneyEvent : AnalyticsEvent {
        data class Opened(val source: Analytics.AddMoneySource) : AddMoneyEvent {
            override val name = "Add Money: Opened"
            override fun toProperties() = mapOf("Source" to source.propertyValue)
        }

        data class MethodSelected(val method: Analytics.AddMoneyMethod) : AddMoneyEvent {
            override val name = "Add Money: Method Selected"
            override fun toProperties() = mapOf("Method" to method.propertyValue)
        }

        data class AmountConfirmed(
            val method: Analytics.AddMoneyMethod,
            val amount: Fiat,
        ) : AddMoneyEvent {
            override val name = "Add Money: Amount Confirmed"
            override fun toProperties() = buildMap {
                put("Method", method.propertyValue)
                putAll(amount.asProperties())
            }
        }

        data class PaymentInvoked(
            val method: Analytics.AddMoneyMethod,
            val amount: Fiat,
        ) : AddMoneyEvent {
            override val name = "Add Money: Payment Invoked"
            override fun toProperties() = buildMap {
                put("Method", method.propertyValue)
                putAll(amount.asProperties())
            }
        }

        data class AddressCopied(val mint: Mint) : AddMoneyEvent {
            override val name = "Add Money: Address Copied"
            override fun toProperties() = mapOf("Mint" to mint.base58())
        }

        data class Terminal(val method: Analytics.AddMoneyMethod) : AddMoneyEvent {
            override val name = "Add Money"
            override fun toProperties() = mapOf("Method" to method.propertyValue)
        }
    }

    sealed interface OpenTokenInfoEvent : AnalyticsEvent {
        val mint: Mint
        override fun toProperties() = mapOf("Mint" to mint.base58())

        data class Deeplink(override val mint: Mint) : OpenTokenInfoEvent {
            override val name = "Token Info: Opened From Deeplink"
        }
        data class Wallet(override val mint: Mint) : OpenTokenInfoEvent {
            override val name = "Token Info: Opened From Wallet"
        }
        data class Give(override val mint: Mint) : OpenTokenInfoEvent {
            override val name = "Token Info: Opened From Give"
        }
    }

    sealed interface TokenTransactionEvent : AnalyticsEvent {
        val mint: Mint
        val amount: Fiat
        val error: Throwable?

        override fun toProperties() = buildMap {
            put("Mint", mint.base58())
            putAll(amount.asProperties())
            error?.let { put("Error", it.message.orEmpty()) }
        }

        sealed interface Purchase : TokenTransactionEvent {
            data class Reserves(override val mint: Mint, override val amount: Fiat, override val error: Throwable? = null) : Purchase {
                override val name = "Token Purchase With Reserves"
            }
            data class Phantom(override val mint: Mint, override val amount: Fiat, override val error: Throwable? = null) : Purchase {
                override val name = "Token Purchase With Phantom"
            }
            data class Coinbase(override val mint: Mint, override val amount: Fiat, override val error: Throwable? = null) : Purchase {
                override val name = "Token Purchase With Coinbase"
            }
        }

        data class Sell(
            override val mint: Mint,
            override val amount: Fiat,
            val feeAmount: Fiat,
            override val error: Throwable? = null
        ) : TokenTransactionEvent {
            override val name = "Token Sell"
            override fun toProperties() = buildMap {
                put("Mint", mint.base58())
                putAll(amount.asProperties())
                put("Fee", feeAmount.decimalValue.toString())
                error?.let { put("Error", it.message.orEmpty()) }
            }
        }
    }
}

internal val Analytics.AddMoneySource.propertyValue: String
    get() = when (this) {
        Analytics.AddMoneySource.Menu -> "Menu"
        Analytics.AddMoneySource.GiveShortfall -> "Give Shortfall"
        Analytics.AddMoneySource.BuyShortfall -> "Buy Shortfall"
        Analytics.AddMoneySource.Chat -> "Chat"
        Analytics.AddMoneySource.Scanner -> "Scanner"
        Analytics.AddMoneySource.Balance -> "Balance"
    }

internal val Analytics.AddMoneyMethod.propertyValue: String
    get() = when (this) {
        Analytics.AddMoneyMethod.Coinbase -> "Coinbase"
        Analytics.AddMoneyMethod.Phantom -> "Phantom"
        Analytics.AddMoneyMethod.OtherWallet -> "Other Wallet"
        Analytics.AddMoneyMethod.Reserves -> "Reserves"
    }

internal fun LocalFiat.asProperties(): Map<String, String> {
    return buildMap {
        putAll(underlyingTokenAmount.asProperties())
        put("Fiat", nativeAmount.decimalValue.toString())
        put("Exchange Rate", rate.fx.toString())
        put("Currency", rate.currency.name)
        put("Mint", mint.base58())
    }
}


internal fun Fiat.asProperties(): Map<String, String> {
    return buildMap {
        put("Fiat", decimalValue.toString())
        put("Currency", currencyCode.name)
        put("USDC", decimalValue.toString())
        put("Quarks", quarks.toDouble().toString())
    }
}

internal fun Analytics.Transfer.toAnalyticsEvent(): AnalyticsEvent = when (this) {
    is Analytics.Transfer.Initiate.GrabBillStart  -> AnalyticsEvent.GrabBillStart
    is Analytics.Transfer.GrabBill                -> AnalyticsEvent.GrabBill(time = time)
    is Analytics.Transfer.Initiate.GiveBillStart  -> AnalyticsEvent.GiveBillStart
    is Analytics.Transfer.GiveBill                -> AnalyticsEvent.GiveBill
    is Analytics.Transfer.Withdrawal              -> AnalyticsEvent.Withdrawal
    is Analytics.Transfer.ClaimedCashLink         -> AnalyticsEvent.ClaimedCashLink
    is Analytics.Transfer.SentCashLink.Clipboard  -> AnalyticsEvent.SentCashLink(clipboard = true)
    is Analytics.Transfer.SentCashLink.App        -> AnalyticsEvent.SentCashLink(app = name)
    is Analytics.Transfer.SentCash                -> AnalyticsEvent.ChatEvent.SentCash
}