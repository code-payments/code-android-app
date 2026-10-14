package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The jump-to-bottom badge opens at the unread divider's number: [MessagingDelegate.observeUnreadBelow]
 * and [MessagingDelegate.resolveUnreadBoundary] read the same self READ pointer and the same
 * inbound count, so seeded identically they agree.
 */
class UnreadBelowObservationTest {

    private val chatId = ChatId("aabbccdd")
    private val selfId = listOf<Byte>(1, 2, 3)

    private val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)
    private val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true).also {
        every { it.accountId } returns selfId
    }

    init {
        coEvery { messageDataSource.hasAtOrBelow(chatId, any()) } returns true
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
    fun `the badge opens at the divider's count`() = runTest {
        coEvery { memberDataSource.getSelfReadPointerOrNull(chatId, selfId) } returns 2
        every { memberDataSource.observeSelfReadPointer(chatId, selfId) } returns flowOf(2L)
        coEvery { messageDataSource.countInboundAfter(chatId, selfId, 2) } returns 3
        every { messageDataSource.observeInboundCountAfter(chatId, selfId, 2) } returns flowOf(3)
        coEvery { messageDataSource.firstNotSentByAfter(chatId, selfId, 2) } returns 3

        val boundary = delegate.resolveUnreadBoundary(chatId) as UnreadBoundary.At

        assertEquals(3, boundary.count)
        assertEquals(boundary.count, delegate.observeUnreadBelow(chatId).first())
    }

    @Test
    fun `no self row counts nothing`() = runTest {
        every { memberDataSource.observeSelfReadPointer(chatId, selfId) } returns flowOf(null)

        assertEquals(0, delegate.observeUnreadBelow(chatId).first())
    }
}
