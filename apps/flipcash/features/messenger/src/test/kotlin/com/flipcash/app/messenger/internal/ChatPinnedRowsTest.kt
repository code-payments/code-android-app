package com.flipcash.app.messenger.internal

import android.content.ClipboardManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.messenger.internal.link.LinkCardClassifier
import com.flipcash.app.messenger.internal.link.LinkCardResolver
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.app.messenger.internal.pins.ChatPins
import com.flipcash.app.session.CashLinkClaims
import com.flipcash.app.session.ChatCashLinks
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.chat.E2eePolicy
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.Pin
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatDraftStore
import com.flipcash.shared.chat.FeaturedGroupsStore
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import com.flipcash.shared.payments.ContactPaymentDelegate
import com.flipcash.shared.payments.TipPaymentDelegate
import com.flipcash.shared.payments.group.GroupSendPaymentDelegate
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.bytes
import com.getcode.util.resources.ResourceHelper
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.shared.chat.ChatMembership
import com.flipcash.shared.chat.ui.CashBubblePeople
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.utils.hexEncodedString
import kotlinx.coroutines.test.advanceUntilIdle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * A pinned reply draws in the sheet the way it does in the transcript: its body under the quote
 * panel naming the original. The row's bubble has to carry the quote, since the sheet only draws it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatPinnedRowsTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chatId = ChatId(UUID.randomUUID().bytes)
    private val viewerId: ID = UUID.randomUUID().bytes
    private val sentAt = Instant.fromEpochSeconds(1_000)

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true) {
        coEvery { getPinnedMessages(any()) } returns Result.failure(IllegalStateException("no pins in this test"))
        coEvery { sampleChatters(any()) } returns Result.failure(IllegalStateException("not sampled"))
        every { observeMediaSendProgress() } returns emptyFlow()
    }
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true) {
        coEvery { getTokenMetadata(any()) } returns Result.failure(RuntimeException())
        every { observeTokenCache() } returns flowOf(emptyMap())
    }
    private val userManager = mockk<UserManager>(relaxed = true) {
        every { accountId } returns viewerId
    }
    private val resources = mockk<ResourceHelper>(relaxed = true) {
        every { getString(com.flipcash.features.messenger.R.string.title_you) } returns "You"
    }

    private val contactCoordinator = mockk<ContactCoordinator>(relaxed = true)
    private val contactPaymentDelegate = mockk<ContactPaymentDelegate>(relaxed = true)
    private val tipPaymentDelegate = mockk<TipPaymentDelegate>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val verifiedFiatCalculator = mockk<VerifiedFiatCalculator>(relaxed = true)
    private val startChattingPayer = mockk<StartChattingPayer>(relaxed = true)
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
    private val chatDraftStore = mockk<ChatDraftStore>(relaxed = true)

    private fun createViewModel(): ChatViewModel = ChatViewModel(
        chatCoordinator = chatCoordinator,
        wrongKeyChats = com.flipcash.services.chat.WrongKeyChats(),
        mediaUploads = noMediaUploads(),
        e2eePolicy = E2eePolicy(),
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
        recentReactionsStore = mockk(relaxed = true),
        emojiCatalogLoader = mockk(relaxed = true),
        toastController = mockk(relaxed = true),
        userProfileDataSource = mockk(relaxed = true),
        rosterSearch = mockk(relaxed = true),
        featuredGroups = FeaturedGroupsStore(mockk(relaxed = true)),
        lobbyRequests = mockk(relaxed = true),
        hostLobbies = mockk(relaxed = true) { every { state } returns MutableStateFlow(emptyMap()) },
        groupRosters = mockk(relaxed = true) { every { state } returns MutableStateFlow(emptyMap()) },
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
        identifier = null,
    )


    private fun message(id: Long, content: MessageContent, fromSelf: Boolean) = ChatMessage(
        messageId = id,
        senderId = if (fromSelf) viewerId else UUID.randomUUID().bytes,
        content = listOf(content),
        timestamp = sentAt,
        unreadSeq = 0,
        eventSequence = id,
        isFromSelf = fromSelf,
    )

    private fun pinsOf(reply: ChatMessage) = ChatPins().read(
        pins = listOf(Pin(messageId = reply.messageId, pinnedBy = viewerId, pinnedAt = sentAt, message = reply)),
        readVersion = 1,
    )

    private fun ChatViewModel.open() = dispatchEvent(ChatViewModel.Event.ChatFound(chatId))

    @Test
    fun `a pinned reply carries a quote naming its original's sender`() = runTest(mainCoroutineRule.dispatcher) {
        val original = message(7, MessageContent.Text("the original"), fromSelf = true)
        coEvery { chatCoordinator.getMessage(chatId, 7) } returns original
        val reply = message(
            9,
            MessageContent.Reply(repliedMessageId = 7, content = listOf(MessageContent.Text("the answer"))),
            fromSelf = false,
        )
        val vm = createViewModel().also { it.open() }

        val bubble = vm.pinnedRows(pinsOf(reply)).single().bubble

        val quote = assertNotNull(bubble.quote)
        assertEquals(7, quote.messageId)
        assertEquals("You", quote.authorName)
        assertEquals("the original", (quote.snippet as ChatQuoteSnippet.Text).body)
        assertEquals(reply.content.single(), bubble.content)
    }

    @Test
    fun `a pinned message that is not a reply has no quote`() = runTest(mainCoroutineRule.dispatcher) {
        val plain = message(9, MessageContent.Text("hello"), fromSelf = false)
        val vm = createViewModel().also { it.open() }

        assertNull(vm.pinnedRows(pinsOf(plain)).single().bubble.quote)
    }

    // A pinned cash message draws the transcript's bubble, so it gets the same names.

    private fun profile(name: String) = UserProfile(
        displayName = name,
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
    )

    private fun cash(receiver: ID? = null, multiReceiver: List<ID> = emptyList()) = MessageContent.Cash(
        intentId = listOf(0),
        amount = Fiat(fiat = 3.0, currencyCode = CurrencyCode.USD),
        mint = Mint.usdf,
        receiver = receiver,
        multiReceiver = multiReceiver.map { MessageContent.Cash.Payment(it, 1.0, 1_000_000L) },
    )

    private fun ChatViewModel.openGroup() {
        open()
        dispatchEvent(
            ChatViewModel.Event.OnGroupResolved(
                ChatMembership(
                    metadata = mockk<ChatMetadata>(relaxed = true) {
                        every { this@mockk.chatId } returns this@ChatPinnedRowsTest.chatId
                        every { rules } returns null
                    },
                    isMember = true,
                )
            )
        )
    }

    @Test
    fun `a DM's cash names the other member`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel().also { it.open() }
        vm.dispatchEvent(ChatViewModel.Event.OnTipUserResolved(UUID.randomUUID().bytes, profile("Noah Parker")))
        advanceUntilIdle()

        val received = message(9, cash(), fromSelf = false)
        assertEquals(
            CashBubblePeople.Direct("Noah Parker"),
            vm.pinnedRows(pinsOf(received)).single().bubble.cashPeople,
        )
    }

    @Test
    fun `a group send names its sender and its first recipient in selection order`() =
        runTest(mainCoroutineRule.dispatcher) {
            val alex: ID = UUID.randomUUID().bytes
            val sent = message(9, cash(multiReceiver = listOf(alex, viewerId)), fromSelf = false)
            val senderId = sent.senderId!!
            coEvery { chatCoordinator.currentSenderProfiles() } returns mapOf(
                senderId.hexEncodedString() to profile("Fred Wilson"),
                alex.hexEncodedString() to profile("Alex Morgan"),
            )
            val vm = createViewModel().also { it.openGroup() }
            advanceUntilIdle()

            val bubble = vm.pinnedRows(pinsOf(sent)).single().bubble
            // The viewer is a recipient too, but the lead is whoever was picked first.
            assertEquals(CashBubblePeople.Group(sender = "Fred Wilson", leadRecipient = "Alex Morgan"), bubble.cashPeople)
            assertNotNull(bubble.receivedPart)
        }

    @Test
    fun `a group send to someone with no profile yet leaves their name for the bubble to fill`() =
        runTest(mainCoroutineRule.dispatcher) {
            val noah: ID = UUID.randomUUID().bytes
            val vm = createViewModel().also { it.openGroup() }
            advanceUntilIdle()

            val sent = message(9, cash(receiver = noah), fromSelf = true)
            assertEquals(
                CashBubblePeople.Group(sender = null, leadRecipient = null),
                vm.pinnedRows(pinsOf(sent)).single().bubble.cashPeople,
            )
        }

    // A person whose profile resolved with neither a name nor a handle: other rows call them
    // "Flipcash User", but a cash bubble reads "Someone" (docs/specs/cash-bubbles.md), which it
    // draws for a null name.

    @Test
    fun `a group send from a sender with no name leaves it null, not Flipcash User`() =
        runTest(mainCoroutineRule.dispatcher) {
            val alex: ID = UUID.randomUUID().bytes
            val sent = message(9, cash(multiReceiver = listOf(alex, viewerId)), fromSelf = false)
            coEvery { chatCoordinator.currentSenderProfiles() } returns mapOf(
                sent.senderId!!.hexEncodedString() to profile(""),
                alex.hexEncodedString() to profile("Alex Morgan"),
            )
            every { resources.getString(com.flipcash.features.messenger.R.string.title_unnamedUser) } returns "Flipcash User"
            val vm = createViewModel().also { it.openGroup() }
            advanceUntilIdle()

            assertEquals(
                CashBubblePeople.Group(sender = null, leadRecipient = "Alex Morgan"),
                vm.pinnedRows(pinsOf(sent)).single().bubble.cashPeople,
            )
        }

    @Test
    fun `a DM with a member who has no name leaves it null, not Flipcash User`() =
        runTest(mainCoroutineRule.dispatcher) {
            every { resources.getString(com.flipcash.features.messenger.R.string.title_unnamedUser) } returns "Flipcash User"
            val vm = createViewModel().also { it.open() }
            vm.dispatchEvent(ChatViewModel.Event.OnTipUserResolved(UUID.randomUUID().bytes, profile("")))
            advanceUntilIdle()

            assertEquals(
                CashBubblePeople.Direct(null),
                vm.pinnedRows(pinsOf(message(9, cash(), fromSelf = false))).single().bubble.cashPeople,
            )
        }
}
