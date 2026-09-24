package com.flipcash.app.analytics

import com.flipcash.app.core.DisplayNameSource
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
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
