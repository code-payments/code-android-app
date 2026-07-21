package com.flipcash.shared.chat

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedSyncCombinedFeedTest {

    private fun metadata(hex: String, type: ChatType) = ChatMetadata(
        chatId = ChatId(hex),
        type = type,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(1000),
    )

    private fun delegateWith(chatController: ChatController) = FeedSyncDelegate(
        chatController = chatController,
        metadataDataSource = mockk(relaxed = true),
        messageDataSource = mockk(relaxed = true),
        memberDataSource = mockk(relaxed = true),
        stateHolder = mockk(relaxed = true),
        userManager = mockk(relaxed = true),
    )

    @Test
    fun `fetches both contact and tip feeds and merges chats`() = runTest {
        val chatController = mockk<ChatController>(relaxed = true)
        coEvery { chatController.getDmChatFeed(ChatType.CONTACT_DM, any()) } returns
            Result.success(ChatFeedPage(listOf(metadata("aa", ChatType.CONTACT_DM)), null, false))
        coEvery { chatController.getDmChatFeed(ChatType.TIP_DM, any()) } returns
            Result.success(ChatFeedPage(listOf(metadata("bb", ChatType.TIP_DM)), null, false))

        val merged = delegateWith(chatController).fetchCombinedFeed().getOrThrow()

        assertEquals(2, merged.size)
        coVerify { chatController.getDmChatFeed(ChatType.CONTACT_DM, any()) }
        coVerify { chatController.getDmChatFeed(ChatType.TIP_DM, any()) }
    }

    @Test
    fun `contact feed still returned when tip feed fails`() = runTest {
        val chatController = mockk<ChatController>(relaxed = true)
        coEvery { chatController.getDmChatFeed(ChatType.CONTACT_DM, any()) } returns
            Result.success(ChatFeedPage(listOf(metadata("aa", ChatType.CONTACT_DM)), null, false))
        coEvery { chatController.getDmChatFeed(ChatType.TIP_DM, any()) } returns
            Result.failure(RuntimeException("tip feed unavailable"))

        val merged = delegateWith(chatController).fetchCombinedFeed().getOrThrow()

        assertEquals(1, merged.size)
        assertEquals(ChatType.CONTACT_DM, merged.first().type)
    }
}
