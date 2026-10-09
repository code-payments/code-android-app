package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Which page loads may fetch outside links. Every load still prefetches, so group, person and gift
 * cards resolve ahead as before; only the `webLinks` grant depends on the chat.
 */
class MessagingWebPrefetchTest {

    private val chatId = ChatId("aabbccdd")
    private val page = ChatMessage(
        messageId = 1,
        senderId = listOf<Byte>(4, 5, 6),
        content = listOf(MessageContent.Text("https://example.com/a")),
        timestamp = Instant.fromEpochSeconds(1001),
        unreadSeq = 0,
    )

    private class Recorder : MessageLinkPrefetch {
        val grants = mutableListOf<Boolean>()
        override suspend fun prefetch(messages: List<ChatMessage>, wait: Duration, webLinks: Boolean) {
            grants += webLinks
        }
    }

    private suspend fun load(open: ChatId?, type: ChatType, isMember: Boolean): List<Boolean> {
        val messagingController = mockk<ChatMessagingController>(relaxed = true)
        coEvery { messagingController.getMessages(chatId, any()) } returns Result.success(listOf(page))
        val metadata = mockk<ChatMetadataDataSource>(relaxed = true)
        coEvery { metadata.getChatType(chatId) } returns type
        coEvery { metadata.isMember(chatId) } returns isMember
        val state = ChatStateHolder().apply { update { it.copy(activeChat = open) } }
        val recorder = Recorder()
        MessagingDelegate(
            chatController = mockk(relaxed = true),
            messagingController = messagingController,
            metadataDataSource = metadata,
            messageDataSource = mockk(relaxed = true),
            memberDataSource = mockk(relaxed = true),
            notificationManager = mockk(relaxed = true),
            userManager = mockk(relaxed = true),
            stateHolder = state,
            analytics = mockk(relaxed = true),
            senderResolver = mockk(relaxed = true),
            linkPrefetch = recorder,
        ).loadMessages(chatId)
        return recorder.grants
    }

    @Test
    fun `the open group's member gets web links`() = runTest {
        assertEquals(listOf(true), load(open = chatId, type = ChatType.GROUP, isMember = true))
    }

    @Test
    fun `the open group's non-member still prefetches, without web links`() = runTest {
        assertEquals(listOf(false), load(open = chatId, type = ChatType.GROUP, isMember = false))
    }

    @Test
    fun `an open DM gets web links`() = runTest {
        assertEquals(listOf(true), load(open = chatId, type = ChatType.CONTACT_DM, isMember = false))
    }

    @Test
    fun `a chat that is not open still prefetches, without web links`() = runTest {
        assertEquals(listOf(false), load(open = null, type = ChatType.CONTACT_DM, isMember = true))
    }
}
