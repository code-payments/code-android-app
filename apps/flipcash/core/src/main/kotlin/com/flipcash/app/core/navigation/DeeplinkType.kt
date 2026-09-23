package com.flipcash.app.core.navigation

import android.net.Uri
import android.os.Parcelable
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.core.ID
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

    @Serializable data class TipChat(val identifier: ChatIdentifier): DeeplinkType, Navigatable

    /**
     * A group chat invite — `app.flipcash.com/chat/{uuid}`.
     *
     * Carries a [ChatId] rather than a [ChatIdentifier] because a group is only ever addressed by
     * its own id: there is no counterparty to resolve it through, the way a DM has.
     *
     * The chat it opens may be one the viewer has not joined. That is the normal case for an
     * invite, and the chat screen is already the gated preview for it — see `GroupAccess`.
     */
    @Serializable data class GroupChatInvite(val chatId: ChatId): DeeplinkType, Navigatable

    /**
     * A tip card addressed by account id — `flipcash.com/{uuid}`, or the older
     * `app.flipcash.com/tip/{uuid}` that links already shared still carry.
     */
    @Serializable data class Tipcard(val userId: ID): DeeplinkType

    /**
     * A `flipcash.com/{username}` link — the same destination as [Tipcard], addressed by the
     * owner's public handle. The id it resolves to is the server's to supply, so it stays a
     * username all the way to the session.
     */
    @Serializable data class TipcardByUsername(val username: String): DeeplinkType

    @Serializable
    data class EmailVerification(
        val email: String,
        val code: String,
        val origin: String? = null
    ): DeeplinkType, Navigatable

    /**
     * Whether this route may be produced by scanning rather than by tapping a link.
     *
     * An allowlist, stated positively and exhaustively, because the cost of a mistake is
     * one-sided: [Login] carries the account seed and [EmailVerification] carries a verification
     * secret, and a scanned image is something somebody else can put in front of the camera or
     * send as a photo. A new route type is refused until someone adds it here, which is the
     * property an `else -> false` would not have.
     *
     * iOS's equivalent is `ScanViewModel.canScanQR`.
     */
    val isScannable: Boolean
        get() = when (this) {
            is CashLink,
            is TokenInfo,
            is TipChat,
            is Tipcard,
            is TipcardByUsername,
            is GroupChatInvite,
            -> true

            is Login,
            is EmailVerification,
            -> false
        }
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
