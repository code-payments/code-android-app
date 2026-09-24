package com.flipcash.app.analytics

import androidx.core.net.toUri
import com.flipcash.app.core.DisplayNameSource
import com.flipcash.app.core.navigation.DeeplinkType
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
