package com.flipcash.app.analytics

import androidx.core.net.toUri
import com.flipcash.app.core.DisplayNameSource
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

    sealed interface DisplayNameEvent : AnalyticsEvent {
        val source: DisplayNameSource
        override fun toProperties() = mapOf("Source" to source.propertyValue)

        /** The user had no display name before this submission. */
        data class Set(override val source: DisplayNameSource) : DisplayNameEvent {
            override val name = "Display Name Set"
        }

        /** The user replaced an existing display name. */
        data class Updated(override val source: DisplayNameSource) : DisplayNameEvent {
            override val name = "Display Name Updated"
        }
    }

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

    /**
     * The gallery scan path, end to end.
     *
     * [Succeeded] reports the tier and zoom that decoded and [Failed] the time a fruitless search
     * took, which together are what would let the ladder's inherited constants be trimmed — they
     * were carried over from Code and have never been measured against real screenshots.
     */
    sealed interface GalleryScanEvent : AnalyticsEvent {
        data object ImagePicked : GalleryScanEvent {
            override val name = "Gallery Scan: Image Picked"
        }

        data class Succeeded(
            val tier: Int,
            val zoom: Float,
            val time: Long,
        ) : GalleryScanEvent {
            override val name = "Gallery Scan: Succeeded"
            override fun toProperties() = mapOf(
                "Tier" to tier.toString(),
                "Zoom" to zoom.toString(),
                "Time" to time.toString(),
            )
        }

        data class Failed(
            val time: Long,
            /** True when the budget ran out rather than the ladder being walked to the end. */
            val exhausted: Boolean,
        ) : GalleryScanEvent {
            override val name = "Gallery Scan: Failed"
            override fun toProperties() = mapOf(
                "Time" to time.toString(),
                "Exhausted" to exhausted.toString(),
            )
        }
    }

    sealed interface TipCardEvent : AnalyticsEvent {
        data object Scanned : TipCardEvent {
            override val name = "Tip Card Scanned"
        }

        data object Presented : TipCardEvent {
            override val name = "Tip Card Presented"
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
        Analytics.AddMoneySource.UsernameShortfall -> "Username Shortfall"
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

internal val DisplayNameSource.propertyValue: String
    get() = when (this) {
        DisplayNameSource.Onboarding -> "Onboarding"
        DisplayNameSource.MyAccount -> "My Account"
        DisplayNameSource.TipCardSetup -> "Tip Card Setup"
    }
