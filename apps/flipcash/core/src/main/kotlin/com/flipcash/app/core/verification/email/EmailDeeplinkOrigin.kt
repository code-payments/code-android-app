package com.flipcash.app.core.verification.email

import com.flipcash.app.core.AppRoute
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.utils.base64
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
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
                val amountString = amount?.let { Json.encodeToString(Fiat.Companion.serializer(), it) }
                when (source) {
                    is AppRoute.Token.OnRamp -> "onramp|amountentry|${source.mint.base58()}"
                    else -> "onramp|null|$amountString"
                }
            }

            MyAccount -> "myaccount"
        }
    }

    companion object {
        fun fromRoute(route: AppRoute?): EmailDeeplinkOrigin? {
            return when (route) {
                is AppRoute.Token.OnRamp -> {
                    OnRamp(route)
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
                        "menu" -> AppRoute.Sheets.Menu
                        "amountentry" -> {
                            println("deeplink origin amountentry")
                            val mint = splits.getOrNull(2)?.let {
                                println("deeplink mint = $it")
                                Mint(it)
                            }

                            if (mint == null) {
                                println("deeplink mint is null")
                                return null
                            }

                            AppRoute.Token.OnRamp(mint)
                        }
                        else -> null
                    }

                    val amount =
                        splits.getOrNull(3)?.let { Json.decodeFromString(Fiat.Companion.serializer(), it) }

                    OnRamp(source, amount)
                }

                "myaccount" -> MyAccount

                else -> null
            }
        }
    }
}