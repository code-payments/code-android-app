package com.flipcash.app.messenger.internal

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.UnreadBoundary
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.flipcash.shared.chat.models.ChatListItem
import com.getcode.opencode.model.financial.TokenResult
import com.getcode.opencode.model.core.bytes
import com.getcode.opencode.model.financial.DataSource
import com.getcode.solana.keys.Mint
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * A cash row names its token by mint, and a mint the device doesn't hold is looked up on the
 * network. That lookup has no deadline, so offline it never answers. The transcript must draw
 * without it: XCTest Bed drew nothing offline because its first page awaited three such lookups.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatTranscriptTokenNameTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chatId = ChatId(UUID.randomUUID().bytes)
    private val mint = Mint.usdf

    private val cash = MessageContent.Cash(
        intentId = listOf(0),
        amount = Fiat(fiat = 3.0, currencyCode = CurrencyCode.USD),
        mint = mint,
    )

    private val message = ChatMessage(
        messageId = 1,
        senderId = UUID.randomUUID().bytes,
        content = listOf(cash),
        timestamp = Instant.fromEpochSeconds(1_000),
        unreadSeq = 0,
        eventSequence = 1,
        isFromSelf = false,
    )

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true) {
        every { observeMessagesPaged(chatId) } returns flowOf(PagingData.from(listOf(message)))
        every { observeMediaSendProgress() } returns emptyFlow()
        // The transcript holds its first page until these land; a relaxed mock's flow never emits.
        coEvery { resolveUnreadBoundary(chatId) } returns UnreadBoundary.None
        every { observeOldestEncryptedMessageId(chatId) } returns flowOf(null)
        every { observePendingMutations(chatId) } returns flowOf(emptyMap())
        coEvery { sampleChatters(any()) } returns Result.failure(IllegalStateException("not sampled"))
        coEvery { getPinnedMessages(any()) } returns Result.failure(IllegalStateException("no pins"))
    }

    // Answers only when the test says so; until then it behaves like a call made offline.
    private val lookup = CompletableDeferred<Result<TokenResult>>()

    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true) {
        every { cachedToken(any()) } returns null
        every { observeTokenCache() } returns flowOf(emptyMap())
        coEvery { getTokenMetadata(any()) } coAnswers { lookup.await() }
    }

    private suspend fun ChatViewModel.cashRows(): List<MessageContent.Cash> =
        messages.asSnapshot()
            .filterIsInstance<ChatListItem.ContentBubble>()
            .mapNotNull { it.content as? MessageContent.Cash }

    private fun viewModel() = chatViewModel(
        dispatcher = mainCoroutineRule.dispatcher,
        chatCoordinator = chatCoordinator,
        tokenCoordinator = tokenCoordinator,
    ).also { it.dispatchEvent(ChatViewModel.Event.ChatFound(chatId)) }

    @Test
    fun `a cash row draws while its token lookup is still out`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel()

        val row = vm.cashRows().single()

        assertEquals("", row.tokenName)
        lookup.cancel()
    }

    @Test
    fun `a cash row takes its token's name once the lookup answers`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel()
        vm.cashRows()

        val token = mockk<Token> {
            every { name } returns "USDF"
            every { imageUrl } returns "https://example.com/usdf.png"
        }
        lookup.complete(Result.success(TokenResult(token, DataSource.Network)))
        advanceUntilIdle()

        assertEquals("USDF", vm.cashRows().single().tokenName)
    }
}
