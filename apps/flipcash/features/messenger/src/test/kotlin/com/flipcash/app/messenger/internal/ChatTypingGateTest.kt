package com.flipcash.app.messenger.internal

import android.content.ClipboardManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.chat.E2eePolicy
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatHydration
import com.flipcash.shared.chat.ChatMembership
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/**
 * When the composer opens. A contact DM waits for a Cash message, because the payment is what opens
 * it. A tip DM has no wait: the payment that creates it is made from the profile, so the chat only
 * exists once it has been paid.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatTypingGateTest {

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

    private val contactCoordinator = mockk<ContactCoordinator>(relaxed = true)
    private val contact = DeviceContact(
        e164 = "+15551234567",
        androidContactId = 1L,
        displayName = "Ada Lovelace",
        photoUri = null,
        displayNumber = "(555) 123-4567",
    )
    private val chatId = ChatId(UUID.randomUUID().bytes)

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { userManager.accountCluster } returns mockk<AccountCluster>(relaxed = true)
        every { exchange.preferredRate } returns Rate.oneToOne
        every { transactionController.limits } returns MutableStateFlow(null)
        every { tipPaymentDelegate.startChattingFee(any()) } returns flowOf(null)
        coEvery { chatCoordinator.hydrateChat(chatId) } returns ChatHydration.Stored
        every { chatCoordinator.observeMessages(chatId) } returns flowOf(emptyList())
        coEvery { contactCoordinator.resolve(any()) } returns Result.failure(IllegalStateException())
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    private fun createViewModel(): ChatViewModel = ChatViewModel(
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
        featuredGroups = FeaturedGroupsStore(mockk(relaxed = true)),
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
        identifier = null,
    )

    @Test
    fun `a tip DM with no messages has typing enabled`() = runTest {
        every { contactCoordinator.lookupContact(any()) } returns Result.failure(NoSuchElementException())
        coEvery { contactCoordinator.lookupContactByDmChatId(any()) } returns null
        every { chatCoordinator.observeMetadata(chatId) } returns flowOf(
            ChatMembership(
                metadata = mockk<ChatMetadata>(relaxed = true) { every { this@mockk.type } returns ChatType.TIP_DM },
                isMember = true,
            ),
        )

        val viewModel = createViewModel()
        viewModel.dispatchEvent(ChatViewModel.Event.OnChatOpened(ChatIdentifier.ByChatId(chatId)))
        advanceUntilIdle()

        val state = viewModel.stateFlow.value
        assertTrue(state.chatType == ChatType.TIP_DM)
        assertTrue(state.typingConstraints.resolved)
        assertTrue(state.typingConstraints.enabled)
    }

    @Test
    fun `a contact DM with no Cash message has typing disabled`() = runTest {
        every { contactCoordinator.lookupContact(contact.e164) } returns Result.success(contact)

        val viewModel = createViewModel()
        viewModel.dispatchEvent(
            ChatViewModel.Event.OnChatOpened(ChatIdentifier.ByContact(contact, chatId))
        )
        advanceUntilIdle()

        val state = viewModel.stateFlow.value
        assertTrue(state.chatType == ChatType.CONTACT_DM)
        assertTrue(state.typingConstraints.resolved)
        assertFalse(state.typingConstraints.enabled)
    }
}
