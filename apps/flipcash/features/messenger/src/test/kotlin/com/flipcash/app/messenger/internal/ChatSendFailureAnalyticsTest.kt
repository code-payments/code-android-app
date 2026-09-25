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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A failed chat send used to report `State: Success` — the delegate failed, but the analytics
 * event said the payment went through. This pins the fixed behaviour: a failed [ContactPaymentDelegate.send]
 * or [TipPaymentDelegate.send] reports `State: Failure` with an `Error` on both "Sent Cash" and
 * "Sent Tip".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSendFailureAnalyticsTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
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
    private val cashLinkClaims = mockk<CashLinkClaims>(relaxed = true)
    private val chatDraftStore = mockk<ChatDraftStore>(relaxed = true)
    private val recentReactionsStore = mockk<RecentReactionsStore>(relaxed = true)
    private val emojiCatalogLoader = mockk<EmojiCatalogLoader>(relaxed = true)

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
        chatDraftStore = chatDraftStore,
        recentReactionsStore = recentReactionsStore,
        toastController = mockk(relaxed = true),
        emojiCatalogLoader = emojiCatalogLoader,
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
    )

    @Test
    fun `a failed cash send reports State Failure with an Error`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery {
            contactPaymentDelegate.send(any(), any(), any(), any(), any())
        } returns Result.failure(RuntimeException("network down"))

        val contact = DeviceContact(
            e164 = "+15551234567",
            androidContactId = 1L,
            displayName = "Ada Lovelace",
            photoUri = null,
            displayNumber = "(555) 123-4567",
        )
        val chatId = ChatId(UUID.randomUUID().bytes)

        val vm = createViewModel()
        vm.dispatchEvent(ChatViewModel.Event.ChatFound(chatId))
        vm.dispatchEvent(ChatViewModel.Event.OnContactFound(contact))
        vm.dispatchEvent(ChatViewModel.Event.OnSendRequested(amount, token))
        advanceUntilIdle()

        val event = analytics.events.single { it.name == "Sent Cash" }
        assertEquals(PropertyValue.Text(AnalyticsState.FAILURE.value), event.properties["State"])
        assertTrue(event.properties.containsKey("Error"))
    }

    @Test
    fun `a failed tip send reports State Failure with an Error`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery {
            tipPaymentDelegate.send(any(), any(), any(), any(), any(), any())
        } returns Result.failure(RuntimeException("network down"))

        val userId: ID = UUID.randomUUID().bytes
        val profile = mockk<UserProfile>(relaxed = true)

        val vm = createViewModel()
        vm.dispatchEvent(ChatViewModel.Event.OnTipUserResolved(userId, profile))
        vm.dispatchEvent(ChatViewModel.Event.OnSendRequested(amount, token))
        advanceUntilIdle()

        val event = analytics.events.single { it.name == "Sent Tip" }
        assertEquals(PropertyValue.Text(AnalyticsState.FAILURE.value), event.properties["State"])
        assertTrue(event.properties.containsKey("Error"))
    }
}
