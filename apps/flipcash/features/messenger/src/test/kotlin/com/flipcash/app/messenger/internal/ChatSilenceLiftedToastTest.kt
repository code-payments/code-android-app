package com.flipcash.app.messenger.internal

import android.content.ClipboardManager
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.test.TestScope
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.toast.SystemToastController
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.SilenceState
import com.flipcash.services.models.chat.ViewerState
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * The silenced viewer is told once when the read-only pill gives way to the composer, whether the
 * host lifted the silence or its timer ran out, and never for a chat that opened free of one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSilenceLiftedToastTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true) {
        every { observeMediaSendProgress() } returns kotlinx.coroutines.flow.emptyFlow()
        // No load ever lands unless a test emits one.
        every { newestPageLoads() } returns MutableSharedFlow()
    }
    private val toasts = mockk<SystemToastController>(relaxed = true)
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
        coEvery { chatCoordinator.hydrateChat(chatId) } coAnswers { awaitCancellation() }
        coEvery { chatCoordinator.sampleChatters(any()) } returns Result.failure(Exception("offline"))
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    private fun createViewModel(
        identifier: ChatIdentifier? = null,
    ): ChatViewModel = ChatViewModel(
        chatCoordinator = chatCoordinator,
        wrongKeyChats = com.flipcash.services.chat.WrongKeyChats(),
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
        groupSendPayments = mockk(relaxed = true),
        chatDraftStore = mockk(relaxed = true),
        recentReactionsStore = mockk(relaxed = true),
        toastController = toasts,
        emojiCatalogLoader = mockk(relaxed = true),
        userProfileDataSource = mockk(relaxed = true),
        rosterSearch = mockk(relaxed = true),
        featuredGroups = featuredGroups,
        lobbyRequests = mockk(relaxed = true),
        hostLobbies = mockk(relaxed = true) { every { state } returns MutableStateFlow(emptyMap()) },
        groupRosters = mockk(relaxed = true) { every { state } returns MutableStateFlow(emptyMap()) },
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
        identifier = identifier,
    )

    private fun silenced(silence: SilenceState) =
        ChatViewModel.Event.OnViewerStateResolved(ViewerState(restrictions = ViewerState.Restrictions(silence)))

    /** Collects the lift events the screen turns into a floating toast. */
    private fun TestScope.liftsOf(viewModel: ChatViewModel): List<ChatViewModel.Event.SilenceLifted> {
        val lifts = mutableListOf<ChatViewModel.Event.SilenceLifted>()
        backgroundScope.launch(mainCoroutineRule.dispatcher) {
            viewModel.eventFlow.filterIsInstance<ChatViewModel.Event.SilenceLifted>().toList(lifts)
        }
        return lifts
    }

    @Test
    fun `the host lifting the silence toasts once`() = runTest(mainCoroutineRule.dispatcher) {
        val viewModel = createViewModel()
        val lifts = liftsOf(viewModel)
        viewModel.dispatchEvent(openChat)
        viewModel.dispatchEvent(silenced(SilenceState.Forever))
        advanceUntilIdle()

        viewModel.dispatchEvent(ChatViewModel.Event.OnViewerStateResolved(ViewerState()))
        advanceUntilIdle()

        assertEquals(1, lifts.size)
    }

    @Test
    fun `a chat that opens with no silence never toasts`() = runTest(mainCoroutineRule.dispatcher) {
        val viewModel = createViewModel()
        val lifts = liftsOf(viewModel)
        viewModel.dispatchEvent(openChat)
        viewModel.dispatchEvent(ChatViewModel.Event.OnViewerStateResolved(ViewerState()))
        viewModel.dispatchEvent(ChatViewModel.Event.OnViewerStateResolved(null))
        advanceUntilIdle()

        assertEquals(0, lifts.size)
    }

    @Test
    fun `a timed silence running out and the host's unsilence landing together toast once`() = runTest(mainCoroutineRule.dispatcher) {
        val viewModel = createViewModel()
        val lifts = liftsOf(viewModel)
        viewModel.dispatchEvent(openChat)
        viewModel.dispatchEvent(silenced(SilenceState.Until(Clock.System.now() + 50.milliseconds)))
        advanceUntilIdle()
        // The state reads the deadline against the wall clock, so let it pass before the timer's event.
        Thread.sleep(80)

        viewModel.dispatchEvent(ChatViewModel.Event.SilenceLapsed)
        viewModel.dispatchEvent(ChatViewModel.Event.OnViewerStateResolved(ViewerState()))
        advanceUntilIdle()

        assertEquals(1, lifts.size)
    }

    @Test
    fun `a second silence and lift toasts again`() = runTest(mainCoroutineRule.dispatcher) {
        val viewModel = createViewModel()
        val lifts = liftsOf(viewModel)
        viewModel.dispatchEvent(openChat)
        repeat(2) {
            viewModel.dispatchEvent(silenced(SilenceState.Forever))
            advanceUntilIdle()
            viewModel.dispatchEvent(ChatViewModel.Event.OnViewerStateResolved(ViewerState()))
            advanceUntilIdle()
        }

        assertEquals(2, lifts.size)
    }
}
