package com.flipcash.app.messenger.internal.link

import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.shared.chat.MessageLinkPrefetch
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.ui.detectUrls
import com.flipcash.shared.chat.ui.linkableText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Resolves the group and person links in arriving messages into [LinkCardMemory], so a card is
 * drawn resolved on the frame its message first appears. See [MessageLinkPrefetch].
 *
 * Only those two card kinds are fetched ahead: they are the ones whose size depends on the answer.
 * Cash and token cards keep resolving when drawn, as before.
 *
 * Links are found exactly as the transcript finds them -- the same text, the same detection pass,
 * the same classifier -- so a link prefetched here is the link a card is drawn for.
 */
internal class MessageLinkPrefetcher(
    private val classifier: LinkCardClassifier,
    private val memory: LinkCardMemory,
    private val group: suspend (chatId: ChatId) -> Result<LinkCard.GroupInvite.State.Resolved>,
    private val user: suspend (identity: LinkCard.User.Identity) -> Result<LinkCard.User.State.Resolved>,
    dispatchers: DispatcherProvider,
) : MessageLinkPrefetch {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.IO)

    /** In-flight lookups by link, so a link in two arriving batches is asked for once. */
    private val inFlight = ConcurrentHashMap<Any, Deferred<Unit>>()

    override suspend fun prefetch(messages: List<ChatMessage>, wait: Duration) {
        // A lookup for a link the store already answers would be wasted, and on a cold start the
        // store may still be loading; wait briefly for it rather than asking for everything.
        withTimeoutOrNull(LOAD_WAIT) { memory.awaitLoaded() }

        val lookups = messages
            .asSequence()
            .filterNot { it.redacted }
            .flatMap { it.content.asSequence() }
            .mapNotNull { content -> content.linkableText()?.let { classifier.firstCard(detectUrls(it)) } }
            .mapNotNull { card -> lookup(card) }
            .toList()

        if (lookups.isEmpty() || wait <= Duration.ZERO) return
        withTimeoutOrNull(wait) { lookups.awaitAll() }
    }

    private fun lookup(card: LinkCard): Deferred<Unit>? = when (card) {
        is LinkCard.GroupInvite -> card.chatId
            .takeUnless { it in memory.groups }
            ?.let { chatId -> start(chatId) { group(chatId).onSuccess { memory.putGroup(chatId, it) } } }
        is LinkCard.User -> card.identity
            .takeUnless { it in memory.users }
            ?.let { identity -> start(identity) { user(identity).onSuccess { memory.putUser(identity, it) } } }
        is LinkCard.Cash, is LinkCard.TokenInfo -> null
    }

    private fun start(key: Any, block: suspend () -> Unit): Deferred<Unit> =
        inFlight.computeIfAbsent(key) {
            scope.async {
                try {
                    block()
                } finally {
                    inFlight.remove(key)
                }
            }
        }

    private companion object {
        val LOAD_WAIT = 500.milliseconds
    }
}
