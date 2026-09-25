package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun `nothing is sent with nobody picked`() = runTest(scheduler) {
        viewModel().invite(INVITE_URL)
        advanceUntilIdle()

        coVerify(exactly = 0) { chatCoordinator.sendMessage(any(), any(), any()) }
    }

    private companion object {
        const val INVITE_URL = "https://app.flipcash.com/chat/6f1c3a9e"
    }
}
