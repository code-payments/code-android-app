package com.flipcash.app.core.navigation

import android.net.Uri
import android.os.Parcelable
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.services.models.chat.ChatId
import com.getcode.solana.keys.Mint
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
sealed interface DeeplinkType: Parcelable {
    sealed interface Navigatable
    @Serializable data class Login(val entropy: String) : DeeplinkType
    @Serializable data class CashLink(val entropy: String = "") : DeeplinkType

    @Serializable data class TokenInfo(val mint: Mint): DeeplinkType, Navigatable

    @Serializable data class Chat(val identifier: ChatIdentifier): DeeplinkType, Navigatable

    @Serializable
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
