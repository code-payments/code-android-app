package com.flipcash.app.core.verification.email

import com.flipcash.app.core.AppRoute
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.utils.base64
import com.getcode.utils.base58
import com.getcode.utils.decodeBase58
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
                val amountString = amount?.let { Json.Default.encodeToString(Fiat.Companion.serializer(), it) }
                when (source) {
                    is AppRoute.Pool.Details -> {
                        when {
                            source.rendezvous != null -> "onramp|pool|seed_${source.rendezvous.seed.base64}|$amountString"
                            source.poolId != null -> "onramp|pool|id_${source.poolId.base58}|$amountString"
                            else -> "onramp|null|$amountString"
                        }
                    }

                    is AppRoute.Sheets.Menu -> "onramp|menu|$amountString"

                    else -> "onramp|null|$amountString"
                }
            }

            MyAccount -> "myaccount"
        }
    }

    companion object {
        fun fromRoute(route: AppRoute?): EmailDeeplinkOrigin? {
            return when (route) {
                is AppRoute.OnRamp.ProviderList -> {
                    OnRamp(route.from, route.neededAmount)
                }

                is AppRoute.Menu.MyAccount -> MyAccount

                else -> null
            }
        }

        fun deserialize(value: String): EmailDeeplinkOrigin? {
            val splits = value.split("|")
            return when (splits[0]) {
                "onramp" -> {
                    val source = when (splits[1]) {
                        "pool" -> {
                            if (splits[2].startsWith("seed")) {
                                AppRoute.Pool.Details(
                                    rendezvous = Ed25519.createKeyPair(
                                        splits[2].removePrefix("seed_").decodeBase64()
                                    )
                                )
                            } else if (splits[2].startsWith("id")) {
                                AppRoute.Pool.Details(
                                    poolId = splits[2].removePrefix("id_").decodeBase58().toList()
                                )
                            } else {
                                null
                            }
                        }
                        "menu" -> AppRoute.Sheets.Menu

                        else -> null
                    }

                    val amount =
                        splits.getOrNull(3)?.let { Json.Default.decodeFromString(Fiat.Companion.serializer(), it) }

                    OnRamp(source, amount)
                }

                "myaccount" -> MyAccount

                else -> null
            }
        }
    }
}