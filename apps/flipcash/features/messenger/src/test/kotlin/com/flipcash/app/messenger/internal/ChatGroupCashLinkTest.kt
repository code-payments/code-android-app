package com.flipcash.app.messenger.internal

import android.content.ClipboardManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.analytics.PropertyValue
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.app.messenger.internal.link.LinkCardClassifier
import com.flipcash.app.messenger.internal.link.LinkCardResolver
import com.flipcash.app.session.CashLinkClaims
import com.flipcash.app.session.ChatCashLinks
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
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
import io.mockk.verify
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
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.SendLimit
import com.flipcash.app.messenger.internal.groupsend.GroupSendState
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.shared.payments.group.GroupSendPaymentDelegate
import com.flipcash.shared.payments.group.GroupSendPlan
import com.getcode.opencode.model.financial.CurrencyCode
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true) {
        coEvery { getPinnedMessages(any()) } returns Result.failure(IllegalStateException("no pins in this test"))
        coEvery { sampleChatters(any()) } returns Result.failure(IllegalStateException("not sampled"))
        every { observeMediaSendProgress() } returns kotlinx.coroutines.flow.emptyFlow()
    }
    private val contactCoordinator = mockk<ContactCoordinator>(relaxed = true)
    private val contactPaymentDelegate = mockk<ContactPaymentDelegate>(relaxed = true)
    private val tipPaymentDelegate = mockk<TipPaymentDelegate>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val verifiedFiatCalculator = mockk<VerifiedFiatCalculator>(relaxed = true)
    private val startChattingPayer = mockk<StartChattingPayer>(relaxed = true) {
        // The gate is the payer's now; these tests exercise what follows it.
        coEvery { mayProceed(any(), any()) } returns true
    }
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
    private val chatCashLinks = mockk<ChatCashLinks>(relaxed = true)
    private val groupSendPayments = mockk<GroupSendPaymentDelegate>(relaxed = true)
    private val viewerId: ID = UUID.randomUUID().bytes
    private val roster: List<ChatMember> = List(12) { member(UUID.randomUUID().bytes) }
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
        every { userManager.accountId } returns viewerId
        coEvery {
            groupSendPayments.send(any(), any(), any(), any(), any(), any())
        } returns Result.success(Unit)
        every { exchange.preferredRate } returns Rate.oneToOne
        every { transactionController.limits } returns MutableStateFlow(
            Limits(
                sinceDate = 0L,
                fetchDate = 0L,
                sendLimits = mapOf(CurrencyCode.USD to SendLimit(1000.0, 1000.0, 1000.0)),
                amountUsdTransactedSinceConsumption = Fiat.Zero,
            )
        )
        every { tokenCoordinator.balanceForToken(any<Token>()) } returns Fiat(999.0)
        coEvery { tokenCoordinator.hasGiveableBalance(any()) } returns true
        coEvery { tokenCoordinator.getTokenMetadata(any()) } returns Result.failure(RuntimeException())
        every { tokenCoordinator.observeTokenCache() } returns flowOf(emptyMap())
        every { tipPaymentDelegate.startChattingFee(any()) } returns flowOf(null)
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

    private fun createViewModel(): ChatViewModel = chatViewModel(
        dispatcher = mainCoroutineRule.dispatcher,
        chatCoordinator = chatCoordinator,
        contactCoordinator = contactCoordinator,
        contactPaymentDelegate = contactPaymentDelegate,
        tipPaymentDelegate = tipPaymentDelegate,
        transactionController = transactionController,
        tokenCoordinator = tokenCoordinator,
        exchange = exchange,
        verifiedFiatCalculator = verifiedFiatCalculator,
        startChattingPayer = startChattingPayer,
        userManager = userManager,
        resources = resources,
        analytics = analytics,
        clipboardManager = clipboardManager,
        userFlags = userFlags,
        linkCardClassifier = linkCardClassifier,
        linkCardResolver = linkCardResolver,
        cashLinkClaims = cashLinkClaims,
        chatCashLinks = chatCashLinks,
        groupSendPayments = groupSendPayments,
        chatDraftStore = chatDraftStore,
    )

    private fun member(id: ID) = ChatMember(id, mockk<UserProfile>(relaxed = true), emptyList())

    private fun ChatViewModel.openGroup(isPrivate: Boolean = false) {
        dispatchEvent(ChatViewModel.Event.ChatFound(chatId))
        dispatchEvent(
            ChatViewModel.Event.OnGroupResolved(
                ChatMembership(
                    metadata = mockk<ChatMetadata>(relaxed = true) {
                        every { this@mockk.chatId } returns this@ChatGroupCashLinkTest.chatId
                        every { rules } returns null
                        every { this@mockk.isPrivate } returns isPrivate
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

        assertTrue(events.any { it is ChatViewModel.Event.NavigateToAmountEntry })
    }

    @Test
    fun `a private group's cash button offers Send Money or Request Money`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        val events = recordEvents(vm)
        vm.openGroup(isPrivate = true)

        vm.dispatchEvent(ChatViewModel.Event.OnSendCash)
        advanceUntilIdle()

        val sheet = BottomBarManager.messages.value.single()
        val actions = sheet.actions
        // The spec's sheet is two rows: no title and no Dismiss.
        assertEquals("", sheet.title)
        assertEquals(listOf("money_action_send", "money_action_request"), actions.map { it.testTag })
        assertTrue(events.none { it is ChatViewModel.Event.NavigateToAmountEntry })

        actions.first { it.testTag == "money_action_request" }.onClick()
        advanceUntilIdle()

        assertTrue(ChatViewModel.Event.NavigateToRequestMoney in events)
        // Asking for money spends nothing, so it skips the balance gate.
        coVerify(exactly = 0) { startChattingPayer.mayProceed(any(), any()) }
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

    /** A private group with [roster] and the viewer in it, on Select People with [total] entered. */
    private fun TestScope.selectingPeople(vm: ChatViewModel, total: Double) {
        vm.openGroup(isPrivate = true)
        vm.dispatchEvent(ChatViewModel.Event.OnMembersResolved(listOf(member(viewerId)) + roster))
        enterAmount(vm, total)
        vm.dispatchEvent(ChatViewModel.Event.OnConfirmRequested)
        advanceUntilIdle()
    }

    /** Opens the amount sheet, after which the keypad holds [total] dollars. */
    private fun TestScope.enterAmount(vm: ChatViewModel, total: Double) {
        vm.dispatchEvent(ChatViewModel.Event.TokenUpdated(token))
        vm.dispatchEvent(ChatViewModel.Event.OnSendCash)
        advanceUntilIdle()
        // A private group's cash button asks Send Money or Request Money first.
        BottomBarManager.messages.value.firstOrNull()?.let { sheet ->
            sheet.actions.first { it.testTag == "money_action_send" }.onClick()
            BottomBarManager.clear()
            advanceUntilIdle()
        }
        vm.amountDelegate.onCurrencyChanged(Currency(code = "USD", name = "US Dollar"))
        vm.amountDelegate.setAmount(total)
    }

    private fun ChatViewModel.check(count: Int) = roster.take(count).forEach {
        dispatchEvent(ChatViewModel.Event.OnGroupSendMemberToggled(it.userId))
    }

    @Test
    fun `a public group's amount goes straight to a cash link`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        vm.openGroup(isPrivate = false)
        enterAmount(vm, 5.0)
        vm.dispatchEvent(ChatViewModel.Event.OnConfirmRequested)
        advanceUntilIdle()

        assertNull(vm.stateFlow.value.groupSend)
        coVerify(exactly = 1) { chatCashLinks.sendToChat(chatId, any(), token, owner) }
        coVerify(exactly = 0) { groupSendPayments.send(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a private group's amount moves on to Select People without the viewer`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        selectingPeople(vm, total = 2.0)
        advanceUntilIdle()

        val state = vm.stateFlow.value
        assertEquals(GroupSendState.Stage.People, state.groupSend?.stage)
        assertEquals(Fiat(2.0), state.groupSend?.total)
        assertEquals(roster.map { it.userId }, state.groupSendCandidates.map { it.userId })
        coVerify(exactly = 0) { chatCashLinks.sendToChat(any(), any(), any(), any()) }
    }

    @Test
    fun `an 11th check shows the cap and keeps ten`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        selectingPeople(vm, total = 20.0)
        vm.check(11)
        advanceUntilIdle()

        assertEquals(roster.take(10).map { it.userId }, vm.stateFlow.value.groupSend?.selected)
        assertEquals(1, BottomBarManager.messages.value.size)
    }

    @Test
    fun `a total under a cent each alerts and stays on Select People`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        selectingPeople(vm, total = 0.05)
        vm.check(6)
        vm.dispatchEvent(ChatViewModel.Event.OnGroupSendPeopleConfirmed)
        advanceUntilIdle()

        assertEquals(GroupSendState.Stage.People, vm.stateFlow.value.groupSend?.stage)
        assertEquals(1, BottomBarManager.messages.value.size)
    }

    @Test
    fun `the swipe pays the checked members in the order they were checked`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        val events = recordEvents(vm)
        selectingPeople(vm, total = 2.0)
        roster.take(6).reversed().forEach {
            vm.dispatchEvent(ChatViewModel.Event.OnGroupSendMemberToggled(it.userId))
        }
        vm.dispatchEvent(ChatViewModel.Event.OnGroupSendPeopleConfirmed)
        assertEquals(GroupSendState.Stage.Confirm, vm.stateFlow.value.groupSend?.stage)

        vm.dispatchEvent(ChatViewModel.Event.OnGroupSendRequested)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            groupSendPayments.send(
                chatId = chatId,
                recipients = roster.take(6).reversed().map { it.userId },
                plan = GroupSendPlan.Pay(listOf(34, 34, 33, 33, 33, 33)),
                currency = CurrencyCode.USD,
                token = token,
                source = any(),
            )
        }
        coVerify(exactly = 0) { chatCashLinks.sendToChat(any(), any(), any(), any()) }
        assertTrue(events.any { it is ChatViewModel.Event.SendComplete })
    }

    @Test
    fun `a group send total above the balance passes the amount step and stops at confirm`() = runTest(mainCoroutineRule.dispatcher) {
        every { tokenCoordinator.balanceForToken(any<Token>()) } returns Fiat(1.0)
        val vm = createViewModel()
        selectingPeople(vm, total = 5.0)

        // The amount step does not check the balance: it moves on to Select People.
        assertEquals(GroupSendState.Stage.People, vm.stateFlow.value.groupSend?.stage)
        verify(exactly = 0) { startChattingPayer.presentInsufficientBalance(any()) }

        vm.check(2)
        vm.dispatchEvent(ChatViewModel.Event.OnGroupSendPeopleConfirmed)
        assertEquals(GroupSendState.Stage.Confirm, vm.stateFlow.value.groupSend?.stage)

        vm.dispatchEvent(ChatViewModel.Event.OnGroupSendRequested)
        advanceUntilIdle()

        verify(exactly = 1) { startChattingPayer.presentInsufficientBalance(any()) }
        coVerify(exactly = 0) { groupSendPayments.send(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a group send has no hint or ceiling on the amount step`() = runTest(mainCoroutineRule.dispatcher) {
        every { tokenCoordinator.balanceForToken(any<Token>()) } returns Fiat(1.0)
        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.amountDelegate.config.collect { } }
        vm.openGroup(isPrivate = true)
        enterAmount(vm, 5.0)
        advanceUntilIdle()
        val hint = vm.amountDelegate.config.value.hint
        assertEquals(com.flipcash.shared.amountentry.AmountEntryHint.None, hint)
        assertTrue(vm.amountDelegate.config.value.canConfirm)
    }

    private fun ChatViewModel.checkYou() =
        dispatchEvent(ChatViewModel.Event.OnGroupSendMemberToggled(viewerId))

    private fun ChatViewModel.checkMember(index: Int) =
        dispatchEvent(ChatViewModel.Event.OnGroupSendMemberToggled(roster[index].userId))

    @Test
    fun `thirty dollars between You and two others pays ten each and charges twenty`() = runTest(mainCoroutineRule.dispatcher) {
        // Within the balance of the paid parts ($20) but not of the entered total ($30).
        every { tokenCoordinator.balanceForToken(any<Token>()) } returns Fiat(25.0)
        val vm = createViewModel()
        selectingPeople(vm, total = 30.0)
        vm.checkYou(); vm.checkMember(0); vm.checkMember(1)
        vm.dispatchEvent(ChatViewModel.Event.OnGroupSendPeopleConfirmed)

        val groupSend = vm.stateFlow.value.groupSend
        assertEquals(GroupSendState.Stage.Confirm, groupSend?.stage)
        assertEquals(Fiat(30.0), groupSend?.total)
        assertEquals(GroupSendPlan.Pay(listOf(1000L, 1000L), viewerPart = 1000L), groupSend?.plan)

        vm.dispatchEvent(ChatViewModel.Event.OnGroupSendRequested)
        advanceUntilIdle()

        verify(exactly = 0) { startChattingPayer.presentInsufficientBalance(any()) }
        coVerify(exactly = 1) {
            groupSendPayments.send(
                chatId = chatId,
                recipients = listOf(roster[0].userId, roster[1].userId),
                plan = GroupSendPlan.Pay(listOf(1000L, 1000L), viewerPart = 1000L),
                currency = CurrencyCode.USD,
                token = token,
                source = any(),
            )
        }
    }

    @Test
    fun `You is the last share whatever order it was checked in, so the others take the extra cent`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        selectingPeople(vm, total = 1.0)
        vm.checkYou(); vm.checkMember(0); vm.checkMember(1)

        assertEquals(GroupSendPlan.Pay(listOf(34L, 33L), viewerPart = 33L), vm.stateFlow.value.groupSend?.plan)
        assertEquals(listOf(roster[0].userId, roster[1].userId), vm.stateFlow.value.groupSend?.paid)
    }

    @Test
    fun `only You checked pays no one and Next goes nowhere`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        selectingPeople(vm, total = 1.0)
        vm.checkYou()
        vm.dispatchEvent(ChatViewModel.Event.OnGroupSendPeopleConfirmed)
        advanceUntilIdle()

        val groupSend = vm.stateFlow.value.groupSend
        assertTrue(groupSend?.paid?.isEmpty() == true)
        assertEquals(GroupSendPlan.NoRecipients, groupSend?.plan)
        assertEquals(GroupSendState.Stage.People, groupSend?.stage)
    }

    @Test
    fun `You counts toward the cap so nine others plus You is the most`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        selectingPeople(vm, total = 20.0)
        vm.checkYou()
        vm.check(10)
        advanceUntilIdle()

        val groupSend = vm.stateFlow.value.groupSend
        assertEquals(9, groupSend?.paid?.size)
        assertEquals(10, groupSend?.selected?.size)
        assertTrue(groupSend?.plan is GroupSendPlan.Pay)
        assertEquals(1, BottomBarManager.messages.value.size)
    }

    @Test
    fun `the too-small check counts You in the split`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        selectingPeople(vm, total = 0.05)
        vm.checkYou(); vm.check(5)
        vm.dispatchEvent(ChatViewModel.Event.OnGroupSendPeopleConfirmed)
        advanceUntilIdle()

        assertEquals(GroupSendPlan.PartBelowMinimum, vm.stateFlow.value.groupSend?.plan)
        assertEquals(GroupSendState.Stage.People, vm.stateFlow.value.groupSend?.stage)
        assertEquals(1, BottomBarManager.messages.value.size)
    }

    @Test
    fun `ten dollars between You and one member says only five is sent, including You`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        selectingPeople(vm, total = 10.0)
        vm.checkYou(); vm.checkMember(0)

        val groupSend = vm.stateFlow.value.groupSend
        assertEquals(Fiat(10.0), groupSend?.total)
        val pay = groupSend?.plan as GroupSendPlan.Pay
        assertEquals(listOf(500L), pay.parts)
        assertEquals(500L, pay.viewerPart)
        // The Each line shows for one paid member when You are in, as "$5.00 Each, including You".
        assertTrue(pay.showsEach && pay.includesViewer && pay.isEven)
        assertEquals(500L, pay.smallest)
    }

    @Test
    fun `one paid member without You has no Each line`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        selectingPeople(vm, total = 10.0)
        vm.checkMember(0)

        val pay = vm.stateFlow.value.groupSend?.plan as GroupSendPlan.Pay
        assertTrue(!pay.showsEach)
    }
}
