package com.flipcash.app.messenger.internal

import androidx.lifecycle.SavedStateHandle
import android.content.ClipboardManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.chat.E2eePolicy
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/**
 * Which chat opens fetch a transcript. A DM opened on its derived id before either side has written
 * in it does not exist on the server, and asking for its messages comes back DENIED, so that open
 * must not ask. Every other outcome still loads as it did.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatOpenTranscriptTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true) {
        every { observeMediaSendProgress() } returns kotlinx.coroutines.flow.emptyFlow()
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
        handle: SavedStateHandle = SavedStateHandle(),
    ): ChatViewModel = ChatViewModel(
        chatCoordinator = chatCoordinator,
        mediaUploads = noMediaUploads(),
        e2eePolicy = E2eePolicy(),
        contactCoordinator = contactCoordinator,
        contactPaymentDelegate = mockk(relaxed = true),
        tipPaymentDelegate = tipPaymentDelegate,
        transactionController = transactionController,
        tokenCoordinator = mockk(relaxed = true),
        exchange = exchange,
        verifiedFiatCalculator = mockk(relaxed = true),
        startChattingPayer = mockk(relaxed = true),
        userManager = userManager,
        resources = mockk(relaxed = true),
        analytics = RecordingAnalytics(),
        clipboardManager = mockk<ClipboardManager>(relaxed = true),
        userFlags = mockk(relaxed = true),
        linkCardClassifier = mockk(relaxed = true),
        linkCardResolver = mockk(relaxed = true),
        cashLinkClaims = mockk(relaxed = true) { every { claimInFlight } returns MutableStateFlow(null) },
        chatCashLinks = mockk(relaxed = true),
        chatDraftStore = mockk(relaxed = true),
        recentReactionsStore = mockk(relaxed = true),
        toastController = mockk(relaxed = true),
        emojiCatalogLoader = mockk(relaxed = true),
        userProfileDataSource = mockk(relaxed = true),
        rosterSearch = mockk(relaxed = true),
        featuredGroups = featuredGroups,
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
        savedStateHandle = handle,
    )

    private fun fetched(type: ChatType) = ChatHydration.Fetched(
        ChatMembership(
            metadata = mockk<ChatMetadata>(relaxed = true) { every { this@mockk.type } returns type },
            isMember = null,
        ),
    )

    @Test
    fun `a DM the server has no record of is not fetched`() = runTest {
        coEvery { chatCoordinator.hydrateChat(chatId) } returns ChatHydration.Absent

        createViewModel().dispatchEvent(openChat)
        advanceUntilIdle()

        coVerify(exactly = 0) { chatCoordinator.loadMessages(any()) }
    }

    @Test
    fun `a chat already on the device is fetched`() = runTest {
        coEvery { chatCoordinator.hydrateChat(chatId) } returns ChatHydration.Stored

        createViewModel().dispatchEvent(openChat)
        advanceUntilIdle()

        coVerify(exactly = 1) { chatCoordinator.loadMessages(chatId) }
    }

    @Test
    fun `a failed lookup still fetches, since it says nothing about the chat`() = runTest {
        coEvery { chatCoordinator.hydrateChat(chatId) } returns ChatHydration.Unavailable

        createViewModel().dispatchEvent(openChat)
        advanceUntilIdle()

        coVerify(exactly = 1) { chatCoordinator.loadMessages(chatId) }
    }

    @Test
    fun `a fetched DM is fetched`() = runTest {
        coEvery { chatCoordinator.hydrateChat(chatId) } returns fetched(ChatType.TIP_DM)

        createViewModel().dispatchEvent(openChat)
        advanceUntilIdle()

        coVerify(exactly = 1) { chatCoordinator.loadMessages(chatId) }
    }

    @Test
    fun `a featured group draws from the row that was tapped before GetChat answers`() = runTest {
        featuredGroups.remember(
            listOf(
                mockk<ChatMetadata>(relaxed = true) {
                    every { this@mockk.chatId } returns this@ChatOpenTranscriptTest.chatId
                    every { type } returns ChatType.GROUP
                    every { title } returns "Moony"
                },
            ),
        )
        coEvery { chatCoordinator.hydrateChat(chatId) } coAnswers { awaitCancellation() }

        val viewModel = createViewModel()
        viewModel.dispatchEvent(openChat)
        runCurrent()

        coVerify { chatCoordinator.sampleChatters(chatId) }
        val group = viewModel.stateFlow.value.subject as ChatSubject.Group
        assertEquals("Moony", group.groupTitle)
        assertNull(group.isMember)
    }

    @Test
    fun `a stored chat is not drawn from the featured row`() = runTest {
        featuredGroups.remember(
            listOf(mockk<ChatMetadata>(relaxed = true) { every { this@mockk.chatId } returns this@ChatOpenTranscriptTest.chatId }),
        )
        every { chatCoordinator.observeMetadata(chatId) } returns flowOf(mockk(relaxed = true))
        coEvery { chatCoordinator.hydrateChat(chatId) } coAnswers { awaitCancellation() }

        val viewModel = createViewModel()
        viewModel.dispatchEvent(openChat)
        runCurrent()

        assertNull(viewModel.stateFlow.value.subject as? ChatSubject.Group)
    }

    @Test
    fun `a group in the feed is drawn from its feed row as soon as it opens`() = runTest {
        val row = mockk<ChatMetadata>(relaxed = true) {
            every { this@mockk.chatId } returns this@ChatOpenTranscriptTest.chatId
            every { type } returns ChatType.GROUP
            every { title } returns "Moony"
        }
        every { chatCoordinator.state } returns MutableStateFlow(ChatState(feed = listOf(row)))
        coEvery { chatCoordinator.hydrateChat(chatId) } coAnswers { awaitCancellation() }

        val viewModel = createViewModel(openedOn(ChatIdentifier.ByChatId(chatId)))

        // Read before anything runs: the first frame composes from this.
        val state = viewModel.stateFlow.value
        val group = state.subject as ChatSubject.Group
        assertEquals("Moony", group.groupTitle)
        // The feed holds only chats the viewer is in, so a member's transcript is never blurred.
        assertEquals(true, group.isMember)
        assertEquals(ChatType.GROUP, state.chatType)
    }

    @Test
    fun `a chat missing from the feed is not seeded`() = runTest {
        every { chatCoordinator.state } returns MutableStateFlow(ChatState(feed = emptyList()))
        coEvery { chatCoordinator.hydrateChat(chatId) } coAnswers { awaitCancellation() }

        val viewModel = createViewModel(openedOn(ChatIdentifier.ByChatId(chatId)))
        runCurrent()

        assertNull(viewModel.stateFlow.value.subject)
    }

    private fun openedOn(identifier: ChatIdentifier) =
        SavedStateHandle(mapOf(ChatViewModel.ARG_CHAT to identifier))
}
