package com.flipcash.app.messenger.internal

import android.content.ClipboardManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.analytics.PropertyValue
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.messenger.internal.link.LinkCardClassifier
import com.flipcash.app.messenger.internal.link.LinkCardResolver
import com.flipcash.app.session.CashLinkClaims
import com.flipcash.app.session.ChatCashLinks
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatDraftStore
import com.flipcash.shared.chat.ChatMembership
import com.flipcash.shared.payments.ContactPaymentDelegate
import com.flipcash.shared.payments.TipPaymentDelegate
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
 * A group has no participant to pay, so its send goes through [ChatCashLinks] rather than the
 * contact or tip delegates. What the link carries and when it is cancelled is
 * `ChatCashLinkDelegateTest`'s; this pins the routing and the event it reports.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatGroupCashLinkTest {

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
    private val chatCashLinks = mockk<ChatCashLinks>(relaxed = true)
    private val chatDraftStore = mockk<ChatDraftStore>(relaxed = true)

    private val owner = mockk<AccountCluster>(relaxed = true)
    private val token = mockk<Token>(relaxed = true)
    private val chatId = ChatId(UUID.randomUUID().bytes)
    private val amount = Fiat(5.0)
    private val verifiedFiat = VerifiedFiat(LocalFiat(Fiat(5.0), Fiat(5.0)))

    @Before
    fun setUp() {
        BottomBarManager.clear()

        every { userManager.accountCluster } returns owner
        every { exchange.preferredRate } returns Rate.oneToOne
        every { transactionController.limits } returns MutableStateFlow(null)
        every { tokenCoordinator.balanceForToken(any<Token>()) } returns Fiat(999.0)
        coEvery { tokenCoordinator.hasGiveableBalance(any()) } returns true
        coEvery { tokenCoordinator.getTokenMetadata(any()) } returns Result.failure(RuntimeException())
        every { tokenCoordinator.observeTokenCache() } returns flowOf(emptyMap())
        every { tipPaymentDelegate.minimumToOpenDmWith(any()) } returns flowOf(null)
        coEvery {
            verifiedFiatCalculator.compute(any(), any(), any(), any(), any())
        } returns Result.success(verifiedFiat)
        coEvery {
            contactCoordinator.resolve(any())
        } returns Result.success(mockk<PublicKey>(relaxed = true))
        coEvery { chatCashLinks.sendToChat(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery {
            contactPaymentDelegate.send(any(), any(), any(), any(), any())
        } returns Result.success(Unit)
        coEvery {
            tipPaymentDelegate.send(any(), any(), any(), any(), any(), any())
        } returns Result.success(null)
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
        chatCashLinks = chatCashLinks,
        chatDraftStore = chatDraftStore,
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
    )

    private fun ChatViewModel.openGroup() {
        dispatchEvent(ChatViewModel.Event.ChatFound(chatId))
        dispatchEvent(
            ChatViewModel.Event.OnGroupResolved(
                ChatMembership(
                    metadata = mockk<ChatMetadata>(relaxed = true) {
                        every { this@mockk.chatId } returns this@ChatGroupCashLinkTest.chatId
                        every { rules } returns null
                    },
                    isMember = true,
                )
            )
        )
    }

    private fun TestScope.recordEvents(vm: ChatViewModel): List<ChatViewModel.Event> {
        val events = mutableListOf<ChatViewModel.Event>()
        backgroundScope.launch(mainCoroutineRule.dispatcher) { vm.eventFlow.toList(events) }
        return events
    }

    private fun text(value: String) = PropertyValue.Text(value)

    @Test
    fun `send cash in a group opens amount entry`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        val events = recordEvents(vm)
        vm.openGroup()

        vm.dispatchEvent(ChatViewModel.Event.OnSendCash)
        advanceUntilIdle()

        assertTrue(ChatViewModel.Event.NavigateToAmountEntry in events)
        assertTrue(ChatViewModel.Event.NavigateToInitPayment !in events)
    }

    @Test
    fun `a group send posts a cash link and reports it`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        val events = recordEvents(vm)
        vm.openGroup()

        vm.dispatchEvent(ChatViewModel.Event.OnSendRequested(amount, token))
        advanceUntilIdle()

        coVerify(exactly = 1) { chatCashLinks.sendToChat(chatId, verifiedFiat, token, owner) }
        coVerify(exactly = 0) { contactPaymentDelegate.send(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { tipPaymentDelegate.send(any(), any(), any(), any(), any(), any()) }
        assertTrue(events.any { it is ChatViewModel.Event.SendComplete })

        val event = analytics.events.single { it.name == "Send Cash Link" }
        assertEquals(text(AnalyticsState.SUCCESS.value), event.properties["State"])
        assertEquals(text("Posted to group chat"), event.properties["Cash Link Choice"])
        assertTrue(analytics.events.none { it.name == "Sent Cash" || it.name == "Sent Tip" })
    }

    @Test
    fun `a failed group send reports State Failure with an Error`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery {
            chatCashLinks.sendToChat(any(), any(), any(), any())
        } returns Result.failure(RuntimeException("offline"))

        val vm = createViewModel()
        val events = recordEvents(vm)
        vm.openGroup()

        vm.dispatchEvent(ChatViewModel.Event.OnSendRequested(amount, token))
        advanceUntilIdle()

        assertTrue(events.none { it is ChatViewModel.Event.SendComplete })
        val event = analytics.events.single { it.name == "Send Cash Link" }
        assertEquals(text(AnalyticsState.FAILURE.value), event.properties["State"])
        assertTrue(event.properties.containsKey("Error"))
    }

    @Test
    fun `a contact send still pays the contact`() = runTest(mainCoroutineRule.dispatcher) {
        val contact = DeviceContact(
            e164 = "+15551234567",
            androidContactId = 1L,
            displayName = "Ada Lovelace",
            photoUri = null,
            displayNumber = "(555) 123-4567",
        )

        val vm = createViewModel()
        vm.dispatchEvent(ChatViewModel.Event.ChatFound(chatId))
        vm.dispatchEvent(ChatViewModel.Event.OnContactFound(contact))
        vm.dispatchEvent(ChatViewModel.Event.OnSendRequested(amount, token))
        advanceUntilIdle()

        coVerify(exactly = 1) { contactPaymentDelegate.send(contact, chatId, verifiedFiat, token, any()) }
        coVerify(exactly = 0) { chatCashLinks.sendToChat(any(), any(), any(), any()) }
        assertEquals(1, analytics.events.count { it.name == "Sent Cash" })
        assertTrue(analytics.events.none { it.name == "Send Cash Link" })
    }

    @Test
    fun `a tip send still pays the user`() = runTest(mainCoroutineRule.dispatcher) {
        val userId: ID = UUID.randomUUID().bytes

        val vm = createViewModel()
        vm.dispatchEvent(ChatViewModel.Event.OnTipUserResolved(userId, mockk<UserProfile>(relaxed = true)))
        vm.dispatchEvent(ChatViewModel.Event.OnSendRequested(amount, token))
        advanceUntilIdle()

        coVerify(exactly = 1) { tipPaymentDelegate.send(userId, verifiedFiat, token, any(), any(), any()) }
        coVerify(exactly = 0) { chatCashLinks.sendToChat(any(), any(), any(), any()) }
        assertTrue(analytics.events.none { it.name == "Send Cash Link" })
    }
}
