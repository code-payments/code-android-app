package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Where the unread divider sits is read once, from the stored self member row. What matters here is
 * the missing row: a group's roster is paged, so the viewer's own row may not be stored, and that
 * has to read as "nothing known" rather than as a pointer of zero, which would put the divider above
 * the whole transcript.
 */
class UnreadBoundaryResolutionTest {

    private val chatId = ChatId("aabbccdd")
    private val selfId = listOf<Byte>(1, 2, 3)

    private val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)
    private val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true).also {
        every { it.accountId } returns selfId
    }

    private val delegate = MessagingDelegate(
        chatController = mockk(relaxed = true),
        messagingController = mockk(relaxed = true),
        metadataDataSource = mockk(relaxed = true),
        messageDataSource = messageDataSource,
        memberDataSource = memberDataSource,
        notificationManager = mockk(relaxed = true),
        userManager = userManager,
        stateHolder = mockk(relaxed = true),
        analytics = mockk(relaxed = true),
        senderResolver = mockk(relaxed = true),
    )

    @Test
    fun `no self row is no divider`() = runTest {
        coEvery { memberDataSource.getSelfReadPointerOrNull(chatId, selfId) } returns null

        assertEquals(UnreadBoundary.None, delegate.resolveUnreadBoundary(chatId))
        coVerify(exactly = 0) { messageDataSource.countInboundAfter(any(), any(), any()) }
    }

    @Test
    fun `nothing unread past the pointer is no divider`() = runTest {
        coEvery { memberDataSource.getSelfReadPointerOrNull(chatId, selfId) } returns 2
        coEvery { messageDataSource.countInboundAfter(chatId, selfId, 2) } returns 0

        assertEquals(UnreadBoundary.None, delegate.resolveUnreadBoundary(chatId))
    }

    @Test
    fun `unread past the pointer places the divider there`() = runTest {
        coEvery { memberDataSource.getSelfReadPointerOrNull(chatId, selfId) } returns 2
        coEvery { messageDataSource.countInboundAfter(chatId, selfId, 2) } returns 3
        coEvery { messageDataSource.firstNotSentByAfter(chatId, selfId, 2) } returns 3

        assertEquals(UnreadBoundary.At(readThrough = 2, count = 3), delegate.resolveUnreadBoundary(chatId))
    }

    /**
     * 1:other 2:self 3:other with the pointer at 1. The divider belongs above 3, and the list places
     * it by comparing neighbours, so the boundary has to sit past the viewer's own message.
     */
    @Test
    fun `the boundary steps over the viewer's own messages after the pointer`() = runTest {
        coEvery { memberDataSource.getSelfReadPointerOrNull(chatId, selfId) } returns 1
        coEvery { messageDataSource.countInboundAfter(chatId, selfId, 1) } returns 1
        coEvery { messageDataSource.firstNotSentByAfter(chatId, selfId, 1) } returns 3

        assertEquals(UnreadBoundary.At(readThrough = 2, count = 1), delegate.resolveUnreadBoundary(chatId))
    }

    @Test
    fun `signed out is no divider`() = runTest {
        every { userManager.accountId } returns null

        assertEquals(UnreadBoundary.None, delegate.resolveUnreadBoundary(chatId))
    }

    @Test
    fun `the walk budget counts every stored row past the id`() = runTest {
        coEvery { messageDataSource.countAfter(chatId, 10) } returns 30

        assertEquals(30, delegate.countMessagesAfter(chatId, 10))
    }
}
