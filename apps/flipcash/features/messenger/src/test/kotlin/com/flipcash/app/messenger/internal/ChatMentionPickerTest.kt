package com.flipcash.app.messenger.internal

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.chat.E2eePolicy
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatMembership
import com.flipcash.shared.chat.MemberMatch
import com.flipcash.shared.chat.RosterSearchSource
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import com.flipcash.app.tokens.TokenCoordinator
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.bytes
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.flipcash.shared.payments.TipPaymentDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals

/**
 * The mention picker's behaviour in the view model: what opens it, what closes it, and what a pick
 * and a send do to the composer's text.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatMentionPickerTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val tipPaymentDelegate = mockk<TipPaymentDelegate>(relaxed = true)
    private val rosterSearch = mockk<RosterSearchSource>(relaxed = true)

    private val chatId = ChatId(UUID.randomUUID().bytes)
    private val erica = match("Érica", "erica")
    private val eric = match("Eric", null)

    @Before
    fun setUp() {
        every { userManager.accountCluster } returns mockk<AccountCluster>(relaxed = true)
        every { exchange.preferredRate } returns Rate.oneToOne
        every { transactionController.limits } returns MutableStateFlow(null)
        every { tokenCoordinator.balanceForToken(any<Token>()) } returns Fiat(999.0)
        every { tokenCoordinator.observeTokenCache() } returns flowOf(emptyMap())
        every { tipPaymentDelegate.minimumToOpenDmWith(any()) } returns flowOf(null)
        coEvery { tokenCoordinator.getTokenMetadata(any()) } returns Result.failure(RuntimeException())
        coEvery { rosterSearch.search(any(), any(), any()) } returns listOf(erica, eric)
        coEvery { chatCoordinator.sendMessage(any(), any(), any()) } returns Result.success(mockk<ChatMessage>(relaxed = true))
    }

    private fun createViewModel(): ChatViewModel = ChatViewModel(
        chatCoordinator = chatCoordinator,
        e2eePolicy = E2eePolicy(),
        contactCoordinator = mockk(relaxed = true),
        contactPaymentDelegate = mockk(relaxed = true),
        tipPaymentDelegate = tipPaymentDelegate,
        transactionController = transactionController,
        tokenCoordinator = tokenCoordinator,
        exchange = exchange,
        verifiedFiatCalculator = mockk(relaxed = true),
        purchaseMethodController = mockk(relaxed = true),
        userManager = userManager,
        resources = mockk(relaxed = true),
        analytics = RecordingAnalytics(),
        clipboardManager = mockk(relaxed = true),
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
        rosterSearch = rosterSearch,
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
    )

    private fun ChatViewModel.openGroup() {
        dispatchEvent(ChatViewModel.Event.ChatFound(chatId))
        dispatchEvent(
            ChatViewModel.Event.OnGroupResolved(
                ChatMembership(
                    metadata = mockk<ChatMetadata>(relaxed = true) {
                        every { this@mockk.chatId } returns this@ChatMentionPickerTest.chatId
                        every { rules } returns null
                        every { rosterSummary.memberCount } returns 4L
                    },
                    isMember = true,
                ),
            ),
        )
    }

    private fun TestScope.type(vm: ChatViewModel, text: String) {
        vm.stateFlow.value.chatInputState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
    }

    private val ChatViewModel.suggestions get() = stateFlow.value.mentionSuggestions
    private val ChatViewModel.text get() = stateFlow.value.chatInputState.text.toString()

    @Test
    fun `an at word in a group lists members with a username`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        vm.openGroup()
        type(vm, "hi @er")

        assertEquals(listOf(erica), vm.suggestions)
        coVerify { rosterSearch.search(chatId, "@er", any()) }
    }

    @Test
    fun `outside a group nothing is searched`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        vm.dispatchEvent(ChatViewModel.Event.ChatFound(chatId))
        type(vm, "hi @er")

        assertEquals(emptyList(), vm.suggestions)
        coVerify(exactly = 0) { rosterSearch.search(any(), any(), any()) }
    }

    @Test
    fun `typing a space closes the list`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        vm.openGroup()
        type(vm, "hi @er")
        type(vm, "hi @er ")

        assertEquals(emptyList(), vm.suggestions)
    }

    @Test
    fun `no matches hides the list`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { rosterSearch.search(any(), any(), any()) } returns listOf(eric)
        val vm = createViewModel()
        vm.openGroup()
        type(vm, "@er")

        assertEquals(emptyList(), vm.suggestions)
    }

    @Test
    fun `picking a member writes their username and a space, and closes the list`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        vm.openGroup()
        type(vm, "thanks @er")

        vm.dispatchEvent(ChatViewModel.Event.PickMention(erica))
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertEquals("thanks @erica ", vm.text)
        assertEquals(emptyList(), vm.suggestions)
    }

    @Test
    fun `cancelling a reply leaves the list open`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        vm.openGroup()
        type(vm, "@er")
        val quote = ChatQuote(messageId = 1, authorName = "Ada", snippet = ChatQuoteSnippet.Text("hi"), accent = null, nameAccent = null)

        vm.dispatchEvent(ChatViewModel.Event.ReplyToMessage(quote))
        advanceUntilIdle()
        assertEquals(listOf(erica), vm.suggestions)

        vm.dispatchEvent(ChatViewModel.Event.CancelReply)
        advanceUntilIdle()
        assertEquals(null, vm.stateFlow.value.replyingTo)
        assertEquals(listOf(erica), vm.suggestions)
    }

    @Test
    fun `sending with the list open sends the text as typed`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        vm.openGroup()
        type(vm, "hi @er")

        vm.dispatchEvent(ChatViewModel.Event.SendMessage)
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        coVerify { chatCoordinator.sendMessage(chatId, "hi @er", null) }
        assertEquals(emptyList(), vm.suggestions)
    }

    @Test
    fun `the first page is refreshed once per visit`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        vm.openGroup()
        type(vm, "@e")
        type(vm, "@er")
        type(vm, "@er ")
        type(vm, "@er @x")

        coVerify(exactly = 1) { rosterSearch.refresh(chatId) }
    }

    @Test
    fun `the open word searches again when the refresh lands`() = runTest(mainCoroutineRule.dispatcher) {
        val refreshed = CompletableDeferred<Unit>()
        val erin = match("Erin", "erin")
        coEvery { rosterSearch.refresh(chatId) } coAnswers { refreshed.await() }
        val vm = createViewModel()
        vm.openGroup()
        type(vm, "@er")
        assertEquals(listOf(erica), vm.suggestions)

        // The refresh brought in a new joiner; no keystroke follows.
        coEvery { rosterSearch.search(any(), any(), any()) } returns listOf(erica, erin)
        refreshed.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(erica, erin), vm.suggestions)
        coVerify(exactly = 2) { rosterSearch.search(chatId, "@er", any()) }
    }

    @Test
    fun `a refresh landing after the list closed leaves it closed`() = runTest(mainCoroutineRule.dispatcher) {
        val refreshed = CompletableDeferred<Unit>()
        coEvery { rosterSearch.refresh(chatId) } coAnswers { refreshed.await() }
        val vm = createViewModel()
        vm.openGroup()
        type(vm, "@er")
        type(vm, "@er ")

        refreshed.complete(Unit)
        advanceUntilIdle()

        assertEquals(emptyList(), vm.suggestions)
        coVerify(exactly = 1) { rosterSearch.search(any(), any(), any()) }
    }

    private fun match(name: String, username: String?) = MemberMatch(
        userId = name.encodeToByteArray().toList(),
        displayName = name,
        username = username,
        profilePicture = null,
    )
}
