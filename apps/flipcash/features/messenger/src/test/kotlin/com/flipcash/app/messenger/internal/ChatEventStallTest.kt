package com.flipcash.app.messenger.internal

import android.content.ClipboardManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.analytics.PropertyValue
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.messenger.internal.link.LinkCardClassifier
import com.flipcash.app.messenger.internal.link.LinkCardResolver
import com.flipcash.app.session.CashLinkClaims
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.models.UserProfile
import com.flipcash.app.persistence.sources.UserProfileDataSource
import com.flipcash.services.chat.E2eePolicy
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatDraftStore
import com.flipcash.shared.payments.ContactPaymentDelegate
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.libs.emojis.reactions.EmojiCatalogLoader
import com.getcode.libs.emojis.reactions.RecentReactionsStore
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.bytes
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Event handlers share one unbuffered bus, so a handler that suspends inline on the network
 * blocks every event dispatched after it until that call returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatEventStallTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true) {
        every { observeMediaSendProgress() } returns kotlinx.coroutines.flow.emptyFlow()
    }
    private val contactCoordinator = mockk<ContactCoordinator>(relaxed = true)
    private val contactPaymentDelegate = mockk<ContactPaymentDelegate>(relaxed = true)
    private val tipPaymentDelegate = mockk<TipPaymentDelegate>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val verifiedFiatCalculator = mockk<VerifiedFiatCalculator>(relaxed = true)
    private val purchaseMethodController = mockk<PurchaseMethodController>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val analytics = RecordingAnalytics()
    private val clipboardManager = mockk<ClipboardManager>(relaxed = true)
    private val userFlags = mockk<UserFlagsCoordinator>(relaxed = true)
    private val linkCardClassifier = mockk<LinkCardClassifier>(relaxed = true)
    private val linkCardResolver = mockk<LinkCardResolver>(relaxed = true)
    private val cashLinkClaims = mockk<CashLinkClaims>(relaxed = true) {
        every { claimInFlight } returns MutableStateFlow(null)
    }
    private val chatDraftStore = mockk<ChatDraftStore>(relaxed = true)
    private val recentReactionsStore = mockk<RecentReactionsStore>(relaxed = true)
    private val emojiCatalogLoader = mockk<EmojiCatalogLoader>(relaxed = true)
    private val userProfileDataSource = mockk<UserProfileDataSource>(relaxed = true)

    private val token = mockk<Token>(relaxed = true)
    private val amount = Fiat(5.0)
    private val verifiedFiat = VerifiedFiat(LocalFiat(Fiat(5.0), Fiat(5.0)))

    @Before
    fun setUp() {
        BottomBarManager.clear()

        every { userManager.accountCluster } returns mockk<AccountCluster>(relaxed = true)
        every { exchange.preferredRate } returns Rate.oneToOne
        every { transactionController.limits } returns MutableStateFlow(null)
        every { tokenCoordinator.balanceForToken(any<Token>()) } returns Fiat(999.0)
        every { tipPaymentDelegate.minimumToOpenDmWith(any()) } returns flowOf(null)
        coEvery {
            verifiedFiatCalculator.compute(any(), any(), any(), any(), any())
        } returns Result.success(verifiedFiat)
        coEvery {
            contactCoordinator.resolve(any())
        } returns Result.success(mockk<PublicKey>(relaxed = true))
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
        contactPaymentDelegate = contactPaymentDelegate,
        tipPaymentDelegate = tipPaymentDelegate,
        transactionController = transactionController,
        tokenCoordinator = tokenCoordinator,
        exchange = exchange,
        verifiedFiatCalculator = verifiedFiatCalculator,
        purchaseMethodController = purchaseMethodController,
        userManager = userManager,
        resources = resources,
        analytics = analytics,
        clipboardManager = clipboardManager,
        userFlags = userFlags,
        linkCardClassifier = linkCardClassifier,
        linkCardResolver = linkCardResolver,
        cashLinkClaims = cashLinkClaims,
        chatCashLinks = mockk(relaxed = true),
        chatDraftStore = chatDraftStore,
        recentReactionsStore = recentReactionsStore,
        toastController = mockk(relaxed = true),
        emojiCatalogLoader = emojiCatalogLoader,
        userProfileDataSource = userProfileDataSource,
        rosterSearch = mockk(relaxed = true),
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
    )

    @Test
    fun `a reaction refresh that never returns does not hold up later events`() = runTest(mainCoroutineRule.dispatcher) {
        // Offline, the refresh's RPC never returns. The event bus has no buffer, so a handler
        // waiting on it inline stalls every event after it: picked photos never reached the composer.
        coEvery { chatCoordinator.refreshReactions(any(), any()) } coAnswers { awaitCancellation() }
        val chatId = ChatId(UUID.randomUUID().bytes)

        val vm = createViewModel()
        vm.dispatchEvent(ChatViewModel.Event.ChatFound(chatId))
        vm.dispatchEvent(ChatViewModel.Event.RefreshReactionIds(listOf(1L)))
        vm.dispatchEvent(ChatViewModel.Event.RefreshReactionIds(listOf(2L)))
        vm.dispatchEvent(ChatViewModel.Event.AdvanceReadPointer(3L))
        advanceTimeBy(1_000)

        coVerify { chatCoordinator.advanceReadPointer(chatId, 3L) }
    }
}
