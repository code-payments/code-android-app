package com.getcode.chatcipher

import kotlin.io.encoding.Base64

/** What a URL is to the apps' routers, per [InviteLink.route]. */
sealed interface InviteLinkRoute {
    /** `/chat/invite` with a valid secret. [secret] is never in [toString]. */
    class Invite(val secret: ByteArray) : InviteLinkRoute {
        override fun toString(): String = "Invite"
    }

    /** `/chat/invite` without a valid secret: a broken invite link, not a chat. */
    data object InvalidInvite : InviteLinkRoute

    /** `/chat/{uuid}`, a group's plain link, for the existing chat route. */
    data object Chat : InviteLinkRoute

    /** Anything else, left to the existing routes. */
    data object Other : InviteLinkRoute
}

/**
 * The invite link, `https://app.flipcash.com/chat/invite#s=<secret>` (spec Decision 5), with the
 * secret in unpadded base64url. The fragment is `/`-separated `key=value` pairs, as both apps'
 * deep-link parsers read it, which is why the secret cannot use standard base64.
 *
 * The link is as good as the chat key: never log it, its fragment or the secret.
 */
object InviteLink {
    private const val ORIGIN = "https://app.flipcash.com"
    private const val INVITE_PATH = "/chat/invite"
    private const val SECRET_KEY = "s="
    private const val SECRET_SIZE = 32
    private val codec = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    /** @throws ChatCipherException if [secret] is not 32 bytes. */
    @Throws(ChatCipherException::class)
    fun url(secret: ByteArray): String {
        if (secret.size != SECRET_SIZE) throw ChatCipherException("invite secret must be 32 bytes")
        return "$ORIGIN$INVITE_PATH#$SECRET_KEY${codec.encode(secret)}"
    }

    /**
     * The secret in [fragment] (without its `#`), or `null` when it holds none. Exactly one `s=`
     * pair, in canonical unpadded base64url of 32 bytes; other keys are ignored.
     */
    fun parseFragment(fragment: String): ByteArray? {
        val values = fragment.split('/').filter { it.startsWith(SECRET_KEY) }
        val value = values.singleOrNull()?.removePrefix(SECRET_KEY) ?: return null
        val secret = try {
            codec.decode(value)
        } catch (e: IllegalArgumentException) {
            return null
        }
        // Re-encoding rejects non-zero trailing bits, which the decoder accepts.
        if (secret.size != SECRET_SIZE || codec.encode(secret) != value) return null
        return secret
    }

    /**
     * Classifies [url] so a router can match the invite path ahead of `/chat/{uuid}`. Only
     * `https://app.flipcash.com` links are [InviteLinkRoute.Invite], [InviteLinkRoute.InvalidInvite]
     * or [InviteLinkRoute.Chat]; a query string is ignored.
     */
    fun route(url: String): InviteLinkRoute {
        val hash = url.indexOf('#')
        val fragment = if (hash < 0) null else url.substring(hash + 1)
        val beforeFragment = if (hash < 0) url else url.substring(0, hash)
        val query = beforeFragment.indexOf('?')
        val address = if (query < 0) beforeFragment else beforeFragment.substring(0, query)
        if (!address.startsWith("$ORIGIN/", ignoreCase = true)) return InviteLinkRoute.Other
        val path = address.substring(ORIGIN.length)

        if (path == INVITE_PATH || path == "$INVITE_PATH/") {
            val secret = fragment?.let(::parseFragment) ?: return InviteLinkRoute.InvalidInvite
            return InviteLinkRoute.Invite(secret)
        }
        val segments = path.removePrefix("/").split('/')
        if (segments.size >= 2 && segments[0] == "chat" && uuid.matches(segments[1])) return InviteLinkRoute.Chat
        return InviteLinkRoute.Other
    }
}
