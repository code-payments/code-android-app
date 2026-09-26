package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatSummary
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The invite sheet's selection and send (nodes 10330:19549, 10330:23177): the bar waits for a
 * pick, each chat gets the link and then the message, one chat failing leaves the rest alone, and
 * the sheet lands on the chat picked first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupInviteViewModelTest {

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true) {
        every { feed(*anyVararg()) } returns flowOf(emptyList())
        coEvery { sendMessage(any(), any(), any()) } returns Result.success(mockk())
    }
    private val userManager = mockk<UserManager>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)

    private val scheduler = TestCoroutineScheduler()

    private val alice = ChatId(ByteArray(32) { 1 })
    private val bob = ChatId(ByteArray(32) { 2 })
    private val carol = ChatId(ByteArray(32) { 3 })

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = GroupInviteViewModel(chatCoordinator, userManager, resources)

    @Test
    fun `the message bar is hidden until a chat is picked`() {
        val model = viewModel()
        assertFalse(model.state.value.showsComposer)

        model.toggle(alice)
        assertTrue(model.state.value.showsComposer)

        model.toggle(alice)
        assertFalse(model.state.value.showsComposer)
    }

    @Test
    fun `each chat gets the link and then the message`() = runTest(scheduler) {
        val model = viewModel()
        model.toggle(alice)
        model.toggle(bob)
        model.onMessageChanged("You should join this chat!")

        model.invite(INVITE_URL)
        advanceUntilIdle()

        coVerifyOrder {
            chatCoordinator.sendMessage(alice, INVITE_URL, null)
            chatCoordinator.sendMessage(alice, "You should join this chat!", null)
        }
        coVerifyOrder {
            chatCoordinator.sendMessage(bob, INVITE_URL, null)
            chatCoordinator.sendMessage(bob, "You should join this chat!", null)
        }
    }

    @Test
    fun `a blank message sends only the link`() = runTest(scheduler) {
        val model = viewModel()
        model.toggle(alice)
        model.onMessageChanged("   ")

        model.invite(INVITE_URL)
        advanceUntilIdle()

        coVerify(exactly = 1) { chatCoordinator.sendMessage(alice, any(), any()) }
        coVerify { chatCoordinator.sendMessage(alice, INVITE_URL, null) }
    }

    @Test
    fun `the sheet lands on the chat picked first, not the one listed first`() = runTest(scheduler) {
        val model = viewModel()
        model.toggle(carol)
        model.toggle(alice)

        model.invite(INVITE_URL)

        assertEquals(carol, model.invited.first())
    }

    @Test
    fun `one chat failing does not stop the others`() = runTest(scheduler) {
        coEvery { chatCoordinator.sendMessage(alice, any(), any()) } returns
            Result.failure(IllegalStateException("offline"))
        coEvery { chatCoordinator.sendMessage(bob, any(), any()) } throws IllegalStateException("boom")
        val model = viewModel()
        model.toggle(alice)
        model.toggle(bob)
        model.toggle(carol)
        model.onMessageChanged("hi")

        model.invite(INVITE_URL)

        // Still navigates, to the first pick, even though its own send failed.
        assertEquals(alice, model.invited.first())
        coVerify { chatCoordinator.sendMessage(carol, INVITE_URL, null) }
        coVerify { chatCoordinator.sendMessage(carol, "hi", null) }
        // A chat whose link failed is not sent the message that was meant to follow it.
        coVerify(exactly = 0) { chatCoordinator.sendMessage(alice, "hi", any()) }
        coVerify(exactly = 0) { chatCoordinator.sendMessage(bob, "hi", any()) }
    }

    @Test
    fun `the list is the feed as the sheet opened, not later updates`() = runTest(scheduler) {
        val feed = MutableSharedFlow<List<ChatSummary>>(replay = 1)
        every { chatCoordinator.feed(*anyVararg()) } returns feed
        feed.emit(emptyList())

        val model = viewModel()
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), model.state.value.recentChats)

        feed.emit(listOf(mockk(relaxed = true)))
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), model.state.value.recentChats)
    }

    @Test
    fun `the list offers groups as well as 1-1 chats`() {
        viewModel()
        verify { chatCoordinator.feed(ChatType.CONTACT_DM, ChatType.TIP_DM, ChatType.GROUP) }
    }

    @Test
    fun `the group being invited to is left out and cannot be picked`() = runTest(scheduler) {
        val group = ChatId(ByteArray(32) { 9 })
        every { chatCoordinator.feed(*anyVararg()) } returns flowOf(
            listOf(summary(alice, ChatType.CONTACT_DM), summary(group, ChatType.GROUP)),
        )
        val model = viewModel()
        advanceUntilIdle()

        model.inviteTo(group)
        model.toggle(group)

        assertEquals(listOf(alice), model.state.value.invitable?.map { it.chatId })
        assertEquals(emptyList<ChatId>(), model.state.value.selection)
    }

    @Test
    fun `nothing is sent with nobody picked`() = runTest(scheduler) {
        viewModel().invite(INVITE_URL)
        advanceUntilIdle()

        coVerify(exactly = 0) { chatCoordinator.sendMessage(any(), any(), any()) }
    }

    private companion object {
        const val INVITE_URL = "https://app.flipcash.com/chat/6f1c3a9e"
    }

    private fun summary(chatId: ChatId, type: ChatType) = ChatSummary(
        metadata = ChatMetadata(
            chatId = chatId,
            type = type,
            members = emptyList(),
            lastMessage = null,
            lastActivity = Instant.fromEpochSeconds(1000),
        ),
        unreadCount = 0,
    )
}
