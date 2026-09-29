package com.flipcash.app.messenger.internal

import android.content.ClipboardManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.chat.E2eePolicy
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatHydration
import com.flipcash.shared.chat.ChatMembership
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
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

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val tipPaymentDelegate = mockk<TipPaymentDelegate>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)

    private val chatId = ChatId(UUID.randomUUID().bytes)
    private val userId = UUID.randomUUID().bytes
    private val openByUser = ChatViewModel.Event.OnChatOpened(
        ChatIdentifier.ByUser(userId = userId, profile = mockk<UserProfile>(relaxed = true)),
    )

    @Before
    fun setUp() {
        BottomBarManager.clear()

        every { userManager.accountCluster } returns mockk<AccountCluster>(relaxed = true)
        every { exchange.preferredRate } returns Rate.oneToOne
        every { transactionController.limits } returns MutableStateFlow(null)
        every { tipPaymentDelegate.minimumToOpenDmWith(any()) } returns flowOf(null)
        coEvery { chatCoordinator.generateChatId(userId) } returns Result.success(chatId)
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    private fun createViewModel(): ChatViewModel = ChatViewModel(
        chatCoordinator = chatCoordinator,
        e2eePolicy = E2eePolicy(),
        contactCoordinator = mockk(relaxed = true),
        contactPaymentDelegate = mockk(relaxed = true),
        tipPaymentDelegate = tipPaymentDelegate,
        transactionController = transactionController,
        tokenCoordinator = mockk(relaxed = true),
        exchange = exchange,
        verifiedFiatCalculator = mockk(relaxed = true),
        purchaseMethodController = mockk(relaxed = true),
        userManager = userManager,
        resources = mockk(relaxed = true),
        analytics = RecordingAnalytics(),
        clipboardManager = mockk<ClipboardManager>(relaxed = true),
        userFlags = mockk(relaxed = true),
        linkCardClassifier = mockk(relaxed = true),
        linkCardResolver = mockk(relaxed = true),
        cashLinkClaims = mockk(relaxed = true),
        chatCashLinks = mockk(relaxed = true),
        chatDraftStore = mockk(relaxed = true),
        recentReactionsStore = mockk(relaxed = true),
        toastController = mockk(relaxed = true),
        emojiCatalogLoader = mockk(relaxed = true),
        userProfileDataSource = mockk(relaxed = true),
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
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

        createViewModel().dispatchEvent(openByUser)
        advanceUntilIdle()

        coVerify(exactly = 0) { chatCoordinator.loadMessages(any()) }
    }

    @Test
    fun `a chat already on the device is fetched`() = runTest {
        coEvery { chatCoordinator.hydrateChat(chatId) } returns ChatHydration.Stored

        createViewModel().dispatchEvent(openByUser)
        advanceUntilIdle()

        coVerify(exactly = 1) { chatCoordinator.loadMessages(chatId) }
    }

    @Test
    fun `a failed lookup still fetches, since it says nothing about the chat`() = runTest {
        coEvery { chatCoordinator.hydrateChat(chatId) } returns ChatHydration.Unavailable

        createViewModel().dispatchEvent(openByUser)
        advanceUntilIdle()

        coVerify(exactly = 1) { chatCoordinator.loadMessages(chatId) }
    }

    @Test
    fun `a fetched DM is fetched`() = runTest {
        coEvery { chatCoordinator.hydrateChat(chatId) } returns fetched(ChatType.TIP_DM)

        createViewModel().dispatchEvent(openByUser)
        advanceUntilIdle()

        coVerify(exactly = 1) { chatCoordinator.loadMessages(chatId) }
    }
}
