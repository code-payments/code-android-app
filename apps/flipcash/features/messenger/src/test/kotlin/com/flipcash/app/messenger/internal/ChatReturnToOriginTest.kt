package com.flipcash.app.messenger.internal

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatHydration
import com.flipcash.shared.chat.ChatMembership
import com.flipcash.shared.chat.ChatState
import com.flipcash.shared.chat.FeaturedGroupsStore
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.bytes
import com.getcode.opencode.model.financial.Rate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/**
 * A tap on the jump-to-bottom button with origins stacked asks the stored chat where each is, from
 * the top down, and lands on the first one still there rather than paging in one that has gone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatReturnToOriginTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true) {
        coEvery { getPinnedMessages(any()) } returns Result.failure(IllegalStateException("no pins in this test"))
        every { observeMediaSendProgress() } returns kotlinx.coroutines.flow.emptyFlow()
        // No load ever lands unless a test emits one.
        every { newestPageLoads() } returns MutableSharedFlow()
    }
    private val tipPaymentDelegate = mockk<TipPaymentDelegate>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)

    private val chatId = ChatId(UUID.randomUUID().bytes)
    private val contactCoordinator = mockk<ContactCoordinator>(relaxed = true)
    private val openChat = ChatViewModel.Event.OnChatOpened(ChatIdentifier.ByChatId(chatId))
    private val featuredGroups = FeaturedGroupsStore(mockk(relaxed = true))

    @Before
    fun setUp() {
        BottomBarManager.clear()

        every { userManager.accountCluster } returns mockk<AccountCluster>(relaxed = true)
        every { exchange.preferredRate } returns Rate.oneToOne
        every { transactionController.limits } returns MutableStateFlow(null)
        every { tipPaymentDelegate.startChattingFee(any()) } returns flowOf(null)
        every { chatCoordinator.observeMetadata(chatId) } returns flowOf(null)
        coEvery { contactCoordinator.lookupContactByDmChatId(any()) } returns null
        coEvery { chatCoordinator.sampleChatters(any()) } returns Result.failure(Exception("offline"))
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    private fun createViewModel(
        identifier: ChatIdentifier? = null,
    ): ChatViewModel = chatViewModel(
        dispatcher = mainCoroutineRule.dispatcher,
        chatCoordinator = chatCoordinator,
        contactCoordinator = contactCoordinator,
        tipPaymentDelegate = tipPaymentDelegate,
        transactionController = transactionController,
        exchange = exchange,
        userManager = userManager,
        featuredGroups = featuredGroups,
        identifier = identifier,
    )

    @Test
    fun `the button skips an origin gone from the chat and lands on the next`() = runTest {
        coEvery { chatCoordinator.hydrateChat(chatId) } coAnswers { awaitCancellation() }
        coEvery { chatCoordinator.distanceFromNewest(chatId, any()) } returns 2
        coEvery { chatCoordinator.distanceFromNewest(chatId, 20) } returns null
        coEvery { chatCoordinator.distanceFromNewest(chatId, 10) } returns 7
        val viewModel = createViewModel()
        viewModel.dispatchEvent(openChat)
        viewModel.dispatchEvent(ChatViewModel.Event.JumpToMessage(messageId = 5, origin = 10))
        viewModel.dispatchEvent(ChatViewModel.Event.JumpToMessage(messageId = 3, origin = 20))
        advanceUntilIdle()

        viewModel.dispatchEvent(ChatViewModel.Event.ReturnToOrigin)
        advanceUntilIdle()

        val state = viewModel.stateFlow.value
        assertEquals(10L, state.jumpTarget)
        assertEquals(7, state.jumpBudget)
        assertEquals(emptyList<Long>(), state.returnStack)
        coVerify(exactly = 0) { chatCoordinator.loadHistoryThrough(chatId, 20) }
    }

    @Test
    fun `a quote tapped in the message a quote jump landed on jumps again`() = runTest {
        coEvery { chatCoordinator.hydrateChat(chatId) } coAnswers { awaitCancellation() }
        coEvery { chatCoordinator.distanceFromNewest(chatId, 20) } returns 4
        coEvery { chatCoordinator.distanceFromNewest(chatId, 10) } returns 9
        val viewModel = createViewModel()
        viewModel.dispatchEvent(openChat)
        viewModel.dispatchEvent(ChatViewModel.Event.JumpToMessage(messageId = 20, origin = 30))
        advanceUntilIdle()
        assertEquals(20L, viewModel.stateFlow.value.jumpTarget)
        viewModel.dispatchEvent(ChatViewModel.Event.JumpConsumed)
        advanceUntilIdle()

        viewModel.dispatchEvent(ChatViewModel.Event.JumpToMessage(messageId = 10, origin = 20))
        advanceUntilIdle()

        val state = viewModel.stateFlow.value
        assertEquals(10L, state.jumpTarget)
        assertEquals(listOf(30L, 20L), state.returnStack)
    }
}
