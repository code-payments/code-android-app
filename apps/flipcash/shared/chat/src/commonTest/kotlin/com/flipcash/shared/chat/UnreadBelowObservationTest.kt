package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The jump-to-bottom badge opens at the unread divider's number: [MessagingDelegate.observeUnreadBelow]
 * and [MessagingDelegate.resolveUnreadBoundary] read the same self READ pointer and the same
 * inbound count, so seeded identically they agree.
 */
class UnreadBelowObservationTest {

    private val chatId = ChatId("aabbccdd")
    private val selfId = listOf<Byte>(1, 2, 3)

    private val messagingController = mockk<com.flipcash.services.controllers.ChatMessagingController>(relaxed = true)
    private val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)
    private val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)
    private val account = MutableStateFlow(UserManager.State(accountId = selfId))
    private val userManager = mockk<UserManager>(relaxed = true).also {
        every { it.accountId } answers { account.value.accountId }
        every { it.state } returns account
    }

    init {
        coEvery { messageDataSource.hasAtOrBelow(chatId, any()) } returns true
    }

    private val delegate = MessagingDelegate(
        chatController = mockk(relaxed = true),
        messagingController = messagingController,
        metadataDataSource = mockk(relaxed = true),
        messageDataSource = messageDataSource,
        memberDataSource = memberDataSource,
        notifications = mockk(relaxed = true),
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

    @Test
    fun `subscribing before the account is ready counts 0, then the count once it is`() = runTest {
        account.value = UserManager.State(accountId = null)
        every { memberDataSource.observeSelfReadPointer(chatId, selfId) } returns flowOf(2L)
        every { messageDataSource.observeInboundCountAfter(chatId, selfId, 2) } returns flowOf(3)

        delegate.observeUnreadBelow(chatId).test {
            assertEquals(0, awaitItem())

            account.value = UserManager.State(accountId = selfId)
            assertEquals(3, awaitItem())

            // Ready -> not ready -> ready resubscribes rather than keeping the old stream.
            account.value = UserManager.State(accountId = null)
            assertEquals(0, awaitItem())
            account.value = UserManager.State(accountId = selfId)
            assertEquals(3, awaitItem())
        }
    }

    @Test
    fun `a failed load of the newest page does not announce a landing, a later success does`() = runTest {
        delegate.newestPageLoads().test {
            coEvery { messagingController.getMessages(chatId) } returns Result.failure(IllegalStateException("offline"))
            delegate.loadMessages(chatId)
            expectNoEvents()

            coEvery { messagingController.getMessages(chatId) } returns Result.success(emptyList())
            delegate.loadMessages(chatId)
            assertEquals(chatId, awaitItem())
        }
    }

    @Test
    fun `the read pointer for the open waits for the account and the database`() = runTest {
        account.value = UserManager.State(accountId = null)
        val databaseUp = CompletableDeferred<Unit>()
        coEvery { messageDataSource.awaitDatabase() } coAnswers { databaseUp.await() }
        coEvery { memberDataSource.getSelfReadPointerOrNull(chatId, selfId) } returns 40

        val pointer = async { delegate.selfReadPointer(chatId) }
        advanceUntilIdle()
        assertFalse(pointer.isCompleted, "no account yet")

        account.value = UserManager.State(accountId = selfId)
        advanceUntilIdle()
        assertFalse(pointer.isCompleted, "no database yet")

        databaseUp.complete(Unit)
        advanceUntilIdle()
        assertEquals(40L, pointer.await())
    }

    @Test
    fun `the open snapshot waits for the account and the database instead of reporting no inbound`() = runTest {
        account.value = UserManager.State(accountId = null)
        val databaseUp = CompletableDeferred<Unit>()
        coEvery { messageDataSource.awaitDatabase() } coAnswers { databaseUp.await() }
        coEvery { messageDataSource.newestInboundId(chatId, selfId) } returns 15

        val newest = async { delegate.newestInboundId(chatId) }
        advanceUntilIdle()
        assertFalse(newest.isCompleted, "no account yet")

        account.value = UserManager.State(accountId = selfId)
        advanceUntilIdle()
        assertFalse(newest.isCompleted, "no database yet")

        databaseUp.complete(Unit)
        advanceUntilIdle()
        assertTrue(newest.isCompleted)
        assertEquals(15L, newest.await())
    }

    @Test
    fun `the unread boundary waits for the account and the database`() = runTest {
        account.value = UserManager.State(accountId = null)
        val databaseUp = CompletableDeferred<Unit>()
        coEvery { messageDataSource.awaitDatabase() } coAnswers { databaseUp.await() }
        coEvery { memberDataSource.getSelfReadPointerOrNull(chatId, selfId) } returns 2
        coEvery { messageDataSource.countInboundAfter(chatId, selfId, 2) } returns 3
        coEvery { messageDataSource.firstNotSentByAfter(chatId, selfId, 2) } returns 3

        val boundary = async { delegate.resolveUnreadBoundary(chatId) }
        advanceUntilIdle()
        assertFalse(boundary.isCompleted)

        account.value = UserManager.State(accountId = selfId)
        databaseUp.complete(Unit)
        advanceUntilIdle()
        assertEquals(3, (boundary.await() as UnreadBoundary.At).count)
    }
}
