package com.flipcash.app.core.navigation

import android.net.Uri
import android.os.Parcelable
import com.flipcash.app.core.onramp.deeplinks.WalletDeeplinkConnectionResult
import com.flipcash.app.core.onramp.deeplinks.ExternalWalletDeeplinkError
import com.flipcash.app.core.onramp.deeplinks.OnRampDeeplinkOrigin
import com.flipcash.app.core.onramp.deeplinks.WalletDeeplinkSigningResult
import com.getcode.ed25519.Ed25519
import com.getcode.solana.keys.Mint
import com.getcode.vendor.Base58
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface DeeplinkType: Parcelable {
    sealed interface Navigatable
    data class Login(val entropy: String) : DeeplinkType
    data class CashLink(val entropy: String) : DeeplinkType
    data class Pool(val seed: String) : DeeplinkType, Navigatable {
        val rendezvous: Ed25519.KeyPair
            get() = Ed25519.createKeyPair(Base58.decode(seed))
    }

    data class TokenInfo(val mint: Mint): DeeplinkType, Navigatable

    sealed interface ExternalWalletStep {
        val origin: OnRampDeeplinkOrigin
    }

    data class ExternalWalletConnection(
        override val origin: OnRampDeeplinkOrigin,
        val result: WalletDeeplinkConnectionResult?,
        val error: ExternalWalletDeeplinkError? = null
    ): DeeplinkType, ExternalWalletStep, Navigatable

    data class ExternalWalletSignedTransaction(
        override val origin: OnRampDeeplinkOrigin,
        val result: WalletDeeplinkSigningResult?,
        val error: ExternalWalletDeeplinkError? = null
    ): DeeplinkType, ExternalWalletStep, Navigatable

    data class EmailVerification(
        val email: String,
        val code: String,
        val origin: String? = null
    ): DeeplinkType, Navigatable
}

val Uri.fragments: Map<Key, String>
    get() {
        return this.toString().split("/")
            .mapNotNull { fragment ->
                val data = Key.entries
                    .map { key -> key to "${key.value}=" }
                    .filter { (_, prefix) -> fragment.startsWith(prefix) }
                    .firstNotNullOfOrNull { (key, prefix) -> key to fragment.removePrefix(prefix) }

                data ?: return@mapNotNull null
            }.associate { (key, value) -> key to value }
    }

@Suppress("ClassName")
sealed interface Key {
    val value: String

    data object entropy : Key {
        override val value: String = "e"
    }

    data object payload : Key {
        override val value: String = "p"
    }

    // unused
    data object key : Key {
        override val value: String = "k"
    }

    // unused
    data object data : Key {
        override val value: String = "d"
    }

    companion object {
        val entries = listOf(entropy, payload, key, data)
    }
}

private operator fun Regex.contains(text: String?): Boolean =
    text?.let { this.matches(it) } ?: false