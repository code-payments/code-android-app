package com.flipcash.app.messenger.internal

import android.content.ClipboardManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.analytics.GroupGateFunding
import com.flipcash.analytics.GroupInviteSheetSource
import com.flipcash.analytics.PropertyValue
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.messenger.internal.link.LinkCardClassifier
import com.flipcash.app.messenger.internal.link.LinkCardResolver
import com.flipcash.app.session.CashLinkClaims
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.models.JoinChatError
import com.flipcash.services.models.LeaveChatError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatDraftStore
import com.flipcash.shared.chat.ChatMembership
import com.flipcash.shared.chat.GroupAccess
import com.flipcash.shared.payments.ContactPaymentDelegate
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.bytes
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The group events the transcript sends. The member count is the roster the screen held before the
 * action, the gate is reported once per visit rather than on every re-decision, and a failed join
 * or leave carries the RPC's result name rather than the throwable's message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatGroupAnalyticsTest {

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

    private val chatId = ChatId(UUID.randomUUID().bytes)
    private val mint = Mint("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAaaaaaaaaaaa")
    private val gatedRules = ChatRules(
        listener = listOf(ChatRuleRequirement.MinimumBalance(Fiat(10.0), listOf(mint))),
        speaker = emptyList(),
    )

    @Before
    fun setUp() {
        BottomBarManager.clear()

        every { userManager.accountCluster } returns mockk<AccountCluster>(relaxed = true)
        every { exchange.preferredRate } returns Rate.oneToOne
        every { transactionController.limits } returns MutableStateFlow(null)
        every { tokenCoordinator.balanceForToken(any<Token>()) } returns Fiat(999.0)
        every { tipPaymentDelegate.minimumToOpenDmWith(any()) } returns flowOf(null)
        // The gate's rule currency is looked up by mint; the name it resolves to is not under test.
        coEvery { tokenCoordinator.getTokenMetadata(any()) } returns Result.failure(RuntimeException())
        every { tokenCoordinator.observeTokenCache() } returns flowOf(emptyMap())
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
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
    )

    private fun membership(isMember: Boolean, rules: ChatRules? = gatedRules, memberCount: Long = 4L) =
        ChatMembership(
            metadata = mockk<ChatMetadata>(relaxed = true) {
                every { this@mockk.chatId } returns this@ChatGroupAnalyticsTest.chatId
                every { this@mockk.rules } returns rules
                every { rosterSummary.memberCount } returns memberCount
            },
            isMember = isMember,
        )

    private fun ChatViewModel.openGroup(isMember: Boolean, rules: ChatRules? = gatedRules) {
        dispatchEvent(ChatViewModel.Event.ChatFound(chatId))
        dispatchEvent(ChatViewModel.Event.OnGroupResolved(membership(isMember, rules)))
    }

    private fun text(value: String) = PropertyValue.Text(value)

    @Test
    fun `the gate is reported once, with the access it first showed`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        vm.openGroup(isMember = false)
        vm.dispatchEvent(ChatViewModel.Event.OnGroupAccessResolved(GroupAccess.Blocked(gatedRules.listener.single())))
        vm.dispatchEvent(ChatViewModel.Event.OnGroupAccessResolved(GroupAccess.Eligible))
        vm.dispatchEvent(ChatViewModel.Event.OnGroupAccessResolved(GroupAccess.Blocked(gatedRules.listener.single())))
        advanceUntilIdle()

        val event = analytics.events.single { it.name == "Group: Gate Shown" }
        assertEquals(text("Blocked"), event.properties["Access"])
        assertEquals(text(mint.base58()), event.properties["Gate Mint"])
        assertEquals(PropertyValue.Number(4.0), event.properties["Member Count"])
    }

    @Test
    fun `a member never sees the gate`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        vm.openGroup(isMember = true)
        vm.dispatchEvent(ChatViewModel.Event.OnGroupAccessResolved(GroupAccess.Membered))
        advanceUntilIdle()

        assertTrue(analytics.events.none { it.name == "Group: Gate Shown" })
    }

    @Test
    fun `an ungated group reports the gate without a mint`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        vm.openGroup(isMember = false, rules = null)
        vm.dispatchEvent(ChatViewModel.Event.OnGroupAccessResolved(GroupAccess.Eligible))
        advanceUntilIdle()

        val event = analytics.events.single { it.name == "Group: Gate Shown" }
        assertEquals(text("Eligible"), event.properties["Access"])
        assertNull(event.properties["Gate Mint"])
    }

    @Test
    fun `a denied join reports the result name and the count before it`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { chatCoordinator.join(any()) } returns Result.failure(JoinChatError.RulesNotSatisfied())

        val vm = createViewModel()
        vm.openGroup(isMember = false)
        vm.dispatchEvent(ChatViewModel.Event.JoinChat)
        advanceUntilIdle()

        val event = analytics.events.single { it.name == "Group: Joined" }
        assertEquals(text(AnalyticsState.FAILURE.value), event.properties["State"])
        assertEquals(text("RulesNotSatisfied"), event.properties["Error"])
        assertEquals(PropertyValue.Number(4.0), event.properties["Member Count"])
        assertEquals(PropertyValue.Flag(true), event.properties["Gated"])
    }

    @Test
    fun `a join that succeeds carries no error`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { chatCoordinator.join(any()) } returns Result.success(Unit)

        val vm = createViewModel()
        vm.openGroup(isMember = false, rules = null)
        vm.dispatchEvent(ChatViewModel.Event.JoinChat)
        advanceUntilIdle()

        val event = analytics.events.single { it.name == "Group: Joined" }
        assertEquals(text(AnalyticsState.SUCCESS.value), event.properties["State"])
        assertNull(event.properties["Error"])
        assertEquals(PropertyValue.Flag(false), event.properties["Gated"])
    }

    @Test
    fun `a leave that fails in transport reports Network`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { chatCoordinator.leave(any()) } returns
            Result.failure(LeaveChatError.Other(RuntimeException("UNAVAILABLE")))

        val vm = createViewModel()
        vm.openGroup(isMember = true)
        vm.dispatchEvent(ChatViewModel.Event.LeaveConfirmed)
        advanceUntilIdle()

        val event = analytics.events.single { it.name == "Group: Left" }
        assertEquals(text(AnalyticsState.FAILURE.value), event.properties["State"])
        assertEquals(text("Network"), event.properties["Error"])
        assertEquals(PropertyValue.Number(4.0), event.properties["Member Count"])
    }

    @Test
    fun `the invite sheet, its rows, the gate buttons and the profile are each reported`() =
        runTest(mainCoroutineRule.dispatcher) {
            val vm = createViewModel()
            vm.openGroup(isMember = true)
            vm.dispatchEvent(ChatViewModel.Event.InviteSheetOpened(GroupInviteSheetSource.PROFILE))
            vm.dispatchEvent(ChatViewModel.Event.InviteLinkShared)
            vm.dispatchEvent(ChatViewModel.Event.CopyInviteLink)
            vm.dispatchEvent(ChatViewModel.Event.GateFundingTapped(GroupGateFunding.BUY_TOKEN))
            vm.dispatchEvent(ChatViewModel.Event.GroupInfoOpened)
            advanceUntilIdle()

            val opened = analytics.events.single { it.name == "Group: Invite Sheet Opened" }
            assertEquals(text("Profile"), opened.properties["Source"])
            assertEquals(PropertyValue.Number(4.0), opened.properties["Member Count"])

            val methods = analytics.events.filter { it.name == "Group: Invite Shared" }
                .map { it.properties["Method"] }
            assertEquals(listOf(text("Share"), text("Copy")), methods)

            val funding = analytics.events.single { it.name == "Group: Gate Funding Tapped" }
            assertEquals(text("Buy Token"), funding.properties["Method"])
            assertEquals(text(mint.base58()), funding.properties["Gate Mint"])

            val info = analytics.events.single { it.name == "Group: Info Opened" }
            assertEquals(PropertyValue.Number(4.0), info.properties["Member Count"])
            assertEquals(PropertyValue.Flag(true), info.properties["Is Member"])
        }
}
