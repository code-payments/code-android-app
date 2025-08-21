package com.flipcash.app.core.verification.email

import com.flipcash.app.core.NavScreenProvider
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
    data class OnRamp(val source: NavScreenProvider?, val amount: Fiat? = null) :
        EmailDeeplinkOrigin()
    data object MyAccount : EmailDeeplinkOrigin()


    fun serialize(): String {
        return when (this) {
            is OnRamp -> {
                val amountString = amount?.let { Json.Default.encodeToString(Fiat.Companion.serializer(), it) }
                when (source) {
                    is NavScreenProvider.HomeScreen.Pools.ChoiceSelection -> {
                        when {
                            source.rendezvous != null -> "onramp|pool|seed_${source.rendezvous.seed.base64}|$amountString"
                            source.poolId != null -> "onramp|pool|id_${source.poolId.base58}|$amountString"
                            else -> "onramp|null|$amountString"
                        }
                    }

                    is NavScreenProvider.HomeScreen.Menu.Root -> "onramp|menu|$amountString"

                    else -> "onramp|null|$amountString"
                }
            }

            MyAccount -> "myaccount"
        }
    }

    companion object {
        fun fromScreenProvider(provider: NavScreenProvider?): EmailDeeplinkOrigin? {
            return when (provider) {
                is NavScreenProvider.HomeScreen.OnRamp.ProviderList -> {
                    OnRamp(provider.from, provider.neededAmount)
                }

                is NavScreenProvider.HomeScreen.Menu.MyAccount.Root -> MyAccount

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
                                NavScreenProvider.HomeScreen.Pools.ChoiceSelection(
                                    rendezvous = Ed25519.createKeyPair(
                                        splits[2].removePrefix("seed_").decodeBase64()
                                    )
                                )
                            } else if (splits[2].startsWith("id")) {
                                NavScreenProvider.HomeScreen.Pools.ChoiceSelection(
                                    poolId = splits[2].removePrefix("id_").decodeBase58().toList()
                                )
                            } else {
                                null
                            }
                        }
                        "menu" -> NavScreenProvider.HomeScreen.Menu.Root

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