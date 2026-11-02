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
}
