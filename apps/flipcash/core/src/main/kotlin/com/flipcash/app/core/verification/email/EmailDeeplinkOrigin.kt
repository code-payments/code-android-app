package com.flipcash.app.core.verification.email

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.SwapPurpose
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.utils.base64
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.utils.decodeBase64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed class EmailDeeplinkOrigin {
    data class OnRamp(val source: AppRoute?, val amount: Fiat? = null) :
        EmailDeeplinkOrigin()
    data object MyAccount : EmailDeeplinkOrigin()


    fun serialize(): String {
        return when (this) {
            is OnRamp -> {
                val amountString = amount?.let { Json.encodeToString(Fiat.Companion.serializer(), it) }
                when (source) {
                    is AppRoute.Token.Swap -> {
                        val mint = (source.purpose as? SwapPurpose.Buy)?.mint
                        "onramp|amountentry|${mint?.base58()}"
                    }
                    else -> "onramp|null|$amountString"
                }
            }

            MyAccount -> "myaccount"
        }
    }

    companion object {
        fun fromRoute(route: AppRoute?): EmailDeeplinkOrigin? {
            return when (route) {
                is AppRoute.Token.Swap -> OnRamp(route)
                is AppRoute.Menu.MyAccount -> MyAccount
                else -> null
            }
        }

        /**
         * Parse a value produced by [serialize]. Returns null for anything unrecognised or
         * malformed rather than throwing — this runs on `client_data` from an `autoVerify`
         * deeplink, so the input is attacker-controllable and must never crash the caller.
         */
        fun deserialize(value: String): EmailDeeplinkOrigin? {
            val splits = value.split("|")
            return when (splits.getOrNull(0)) {
                "onramp" -> {
                    val source = when (splits.getOrNull(1)) {
                        "amountentry" -> {
                            val mint = splits.getOrNull(2)
                                ?.takeIf { it.isNotBlank() && it != "null" }
                                ?.let { Mint(it) }
                                ?: return null

                            AppRoute.Token.Swap(SwapPurpose.Buy(mint))
                        }
                        // "null" — an on-ramp with no swap source, carrying only an amount.
                        else -> null
                    }

                    val amount = splits.getOrNull(3)
                        ?.takeIf { it.isNotBlank() && it != "null" }
                        ?.let {
                            runCatching {
                                Json.decodeFromString(Fiat.Companion.serializer(), it)
                            }.getOrNull()
                        }

                    OnRamp(source, amount)
                }

                "myaccount" -> MyAccount

                else -> null
            }
        }
    }
}