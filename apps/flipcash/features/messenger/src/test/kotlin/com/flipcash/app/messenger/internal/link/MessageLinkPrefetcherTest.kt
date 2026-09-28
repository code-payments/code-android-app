package com.flipcash.app.messenger.internal.link

import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.router.Router
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.opencode.model.core.bytes
import dev.theolm.rinku.DeepLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MessageLinkPrefetcherTest {

    private val groupUuid = UUID.fromString("0d8e3c7a-5b2f-4a91-8e6d-3c1b0a9f8e7d")
    private val chatId = ChatId(groupUuid.bytes)
    private val inviteText = "join us https://app.flipcash.com/chat/$groupUuid"

    private val resolved = LinkCard.GroupInvite.State.Resolved(
        title = "All Jeffy Holders",
        picture = null,
        memberCount = 42,
        requirement = null,
    )

    /** Classifies group invites and nothing else; the classifier's own rules are tested elsewhere. */
    private val router = object : Router {
        override fun classify(deepLink: DeepLink): DeeplinkType? {
            val segments = deepLink.data.substringAfter("://").split('/').drop(1)
            return if (segments.firstOrNull() == "chat") {
                DeeplinkType.GroupChatInvite(ChatId(UUID.fromString(segments[1]).bytes))
            } else {
                null
            }
        }

        override fun dispatch(deepLink: DeepLink) = error("not used")
    }

    private fun message(text: String, id: Long = 1, redacted: Boolean = false) = ChatMessage(
        messageId = id,
        senderId = null,
        content = listOf(MessageContent.Text(text)),
        timestamp = Instant.fromEpochSeconds(id),
        unreadSeq = id,
        redacted = redacted,
    )

    private fun TestScope.prefetcher(
        memory: LinkCardMemory,
        group: suspend (ChatId) -> Result<LinkCard.GroupInvite.State.Resolved>,
    ) = MessageLinkPrefetcher(
        classifier = LinkCardClassifier(router),
        memory = memory,
        group = group,
        user = { Result.failure(IOException("not used")) },
        dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
    )

    @Test
    fun `an invite in an arriving message is resolved before the message is written`() = runTest {
        val memory = LinkCardMemory()
        prefetcher(memory) { Result.success(resolved) }
            .prefetch(listOf(message(inviteText)), wait = 1.seconds)

        assertEquals(resolved, memory.groups[chatId])
    }

    @Test
    fun `a lookup slower than the wait still lands, after the write has gone ahead`() = runTest {
        val memory = LinkCardMemory()
        val answer = CompletableDeferred<Result<LinkCard.GroupInvite.State.Resolved>>()
        prefetcher(memory) { answer.await() }
            .prefetch(listOf(message(inviteText)), wait = 1.seconds)

        assertNull(memory.groups[chatId])

        answer.complete(Result.success(resolved))
        advanceUntilIdle()
        assertEquals(resolved, memory.groups[chatId])
    }

    @Test
    fun `a link already answered is not asked about again, nor one already being asked about`() = runTest {
        val memory = LinkCardMemory()
        var asked = 0
        val answer = CompletableDeferred<Result<LinkCard.GroupInvite.State.Resolved>>()
        val prefetcher = prefetcher(memory) { asked++; answer.await() }

        prefetcher.prefetch(listOf(message(inviteText, id = 1)))
        prefetcher.prefetch(listOf(message(inviteText, id = 2)))
        advanceUntilIdle()
        answer.complete(Result.success(resolved))
        advanceUntilIdle()
        prefetcher.prefetch(listOf(message(inviteText, id = 3)))
        advanceUntilIdle()

        assertEquals(1, asked)
    }

    @Test
    fun `a failed lookup leaves nothing behind`() = runTest {
        val memory = LinkCardMemory()
        prefetcher(memory) { Result.failure(IOException("offline")) }
            .prefetch(listOf(message(inviteText)), wait = 1.seconds)

        assertTrue(memory.groups.isEmpty())
    }

    @Test
    fun `a redacted message is not read for links`() = runTest {
        val memory = LinkCardMemory()
        var asked = 0
        prefetcher(memory) { asked++; Result.success(resolved) }
            .prefetch(listOf(message(inviteText, redacted = true)), wait = 1.seconds)

        assertEquals(0, asked)
    }
}
