package com.flipcash.app.messenger.internal.link

import android.net.Uri
import androidx.core.net.toUri
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.router.Router
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.ui.DetectedUrl
import dev.theolm.rinku.DeepLink
import javax.inject.Inject

/**
 * Which link in a message, if any, becomes a card.
 *
 * Three gates, in this order.
 *
 * **Unwrap.** `jump.flipcash.com` carries the real link percent-encoded in `#source=`. It is
 * unwrapped here, before anything else looks at the host, because the redirector's own host says
 * nothing about where the link goes.
 *
 * **Host.** [CARD_HOSTS] is checked on an exact match, against the unwrapped URL.
 * [Router.classify] matches on path alone — deliberately, since every URL it normally sees
 * arrived through an `autoVerify` intent filter — so on its own it would classify
 * `send.flipcash.com.evil.com/c/#/e=…` as a cash link. Message text has passed no such gate.
 * `AppRouter` is not widened for this: its own rules are right for routing, and a chat-rendering
 * problem should not change where a tapped link goes.
 *
 * **Route.** Whatever survives goes to the real classifier, which holds the reserved-path list
 * that keeps `flipcash.com/download` a plain link. No second path parser is written.
 *
 * `/login` and `/verify` are excluded by omission — they carry the account seed and a
 * verification secret, and a card with a tap target in front of either is a phishing aid.
 */
internal class LinkCardClassifier @Inject constructor(
    private val router: Router,
) {

    /**
     * The first card-eligible link wins; at most one card per message.
     *
     * The card comes out loading, never resolved: classifying is all that can be done from the
     * message text, and the lookup that fills the rest in belongs to the card that draws it.
     *
     * Takes the detected links rather than their URLs because the card carries the span it came
     * from: the bubble draws the card in place of that text, and only the detection pass knows
     * where it sat.
     */
    fun firstCard(links: List<DetectedUrl>): LinkCard? = links.firstNotNullOfOrNull { classify(it) }

    private fun classify(link: DetectedUrl): LinkCard? {
        val target = unwrapJumpTarget(link.url) ?: link.url
        val host = runCatching { target.toUri().host }.getOrNull() ?: return null
        if (host.lowercase() !in CARD_HOSTS) return null

        return when (val type = router.classify(DeepLink(target))) {
            is DeeplinkType.CashLink -> type.entropy
                .takeIf { it.isNotBlank() }
                ?.let {
                    LinkCard.Cash(
                        url = target,
                        start = link.start,
                        end = link.end,
                        entropy = it,
                        state = LinkCard.Cash.State.Loading,
                    )
                }
            is DeeplinkType.TokenInfo -> LinkCard.TokenInfo(
                url = target,
                start = link.start,
                end = link.end,
                mint = type.mint,
                state = LinkCard.TokenInfo.State.Loading,
            )
            // A group's invite, and only the bare `/chat/{uuid}`. The router claims anything under
            // it, so the send-cash path beneath (`/chat/{uuid}/send`, iOS's `chatSendCash`) is
            // refused here by shape: it opens a payment, not the group. `TipChat` falls to `else`.
            is DeeplinkType.GroupChatInvite -> LinkCard.GroupInvite(
                url = target,
                start = link.start,
                end = link.end,
                chatId = type.chatId,
                state = LinkCard.GroupInvite.State.Loading,
            ).takeIf { target.toUri().pathSegments.size == GROUP_INVITE_SEGMENTS }
            else -> null
        }
    }

    /**
     * Mirrors `AppRouter`'s own unwrap, which is private to that module. Duplicated rather than
     * exported because the card needs the unwrapped URL as a *value* — it is what the card stands
     * for and what resolution is given — not just as an input to classification.
     *
     * Returns null when this is not a jump link, when the fragment is absent or empty, or when the
     * target is itself a jump link. Unwrapping once and refusing to recurse matches the router and
     * iOS `DeepLinkController`; a jump pointing at a jump is malformed.
     */
    private fun unwrapJumpTarget(url: String): String? {
        val uri = runCatching { url.toUri() }.getOrNull() ?: return null
        if (!uri.host.equals(JUMP_HOST, ignoreCase = true)) return null

        val fragment = url.substringAfter('#', missingDelimiterValue = "")
        if (!fragment.startsWith(JUMP_SOURCE_PARAM)) return null

        // Everything after `source=`, not up to the next `&` — the wrapped URL may carry its own
        // query string with `&` separators that the producer left unencoded. iOS does the same.
        val encoded = fragment.removePrefix(JUMP_SOURCE_PARAM).takeIf { it.isNotBlank() } ?: return null

        // Uri.decode, not URLDecoder: the payload is a URL, and `+` in it is a literal plus.
        val target = Uri.decode(encoded)?.takeIf { it.isNotBlank() } ?: return null

        val targetHost = runCatching { target.toUri().host }.getOrNull()
        if (targetHost.equals(JUMP_HOST, ignoreCase = true)) return null

        return target
    }

    companion object {
        /** `chat`, then the id. */
        private const val GROUP_INVITE_SEGMENTS = 2

        private const val JUMP_HOST = "jump.flipcash.com"
        private const val JUMP_SOURCE_PARAM = "source="

        /**
         * The union of the hosts the two apps claim — Android's manifest intent filters and iOS's
         * associated-domains entitlement. `www.flipcash.com` is Android-only for routing and is
         * here anyway: whether a link *is* a Flipcash link is not a question about which app opens
         * it, and the cross-platform fixture has to agree on one answer.
         */
        val CARD_HOSTS = setOf(
            "app.flipcash.com",
            "send.flipcash.com",
            "flipcash.com",
            "www.flipcash.com",
            "jump.flipcash.com",
        )
    }
}
