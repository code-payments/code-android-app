package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.AddReactionError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.Emoji
import com.flipcash.services.models.chat.EmojiReaction
import com.flipcash.services.models.chat.ReactionSummary
import com.flipcash.services.models.chat.Reactor
import com.flipcash.services.repository.ReactorsPage
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.delegates.ReactionsDelegate
import com.getcode.libs.emojis.reactions.RecentReactionsStore
import com.getcode.opencode.model.core.ID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReactionsDelegateTest {

    private lateinit var messagingController: ChatMessagingController
    private lateinit var messageDataSource: ChatMessageDataSource
    private lateinit var recentReactionsStore: RecentReactionsStore
    private lateinit var stateHolder: ChatStateHolder
    private lateinit var delegate: ReactionsDelegate

    private val chatId: ChatId = ChatId("aabbccdd")
    private val selfId: ID = "self-user-id".encodeToByteArray().toList()
    private val emoji = "😀"

    @Before
    fun setup() {
        messagingController = mockk(relaxed = true)
        messageDataSource = mockk(relaxed = true)
        recentReactionsStore = mockk(relaxed = true)
        stateHolder = ChatStateHolder()
        val userManager = mockk<UserManager>(relaxed = true)
        every { userManager.accountId } returns selfId

        delegate = ReactionsDelegate(
            messagingController = messagingController,
            messageDataSource = messageDataSource,
            recentReactionsStore = recentReactionsStore,
            userManager = userManager,
            stateHolder = stateHolder,
        )
    }

    @Test
    fun `toggle success adds a self pill, persists, and records recents`() = runTest {
        coEvery { messagingController.addReaction(chatId, 1L, Emoji(emoji)) } returns Result.success(
            EmojiReaction(
                emoji = Emoji(emoji),
                count = 1,
                selfReactor = Reactor(userId = selfId, reactedAt = Instant.fromEpochSeconds(1000), version = 1),
                sampleReactors = emptyList(),
                version = 1,
            )
        )

        delegate.toggleReaction(chatId, 1L, emoji)

        val reactions = delegate.observeChatReactions(chatId).first()
        val pills = reactions[1L]?.pills
        assertEquals(1, pills?.size)
        assertEquals(emoji, pills?.first()?.emoji)
        assertTrue(pills?.first()?.selfReacted == true)

        coVerify { recentReactionsStore.record(emoji) }
        coVerify { messageDataSource.mergeReactions(chatId, 1L, any()) }
    }

    @Test
    fun `toggle failure rolls back the pill and reports a non-silent error`() = runTest {
        coEvery { messagingController.addReaction(chatId, 1L, Emoji(emoji)) } returns
            Result.failure(AddReactionError.Denied())

        var error: com.flipcash.shared.chat.reactions.ReactionError? = null
        val collector = launch { error = delegate.reactionErrors.first() }
        yield()

        delegate.toggleReaction(chatId, 1L, emoji)
        collector.join()

        // Denied maps to REACTION_FAILED via ReactionFailure.userError.
        assertEquals(com.flipcash.shared.chat.reactions.ReactionError.REACTION_FAILED, error)

        coVerify(exactly = 0) { messageDataSource.mergeReactions(any(), any(), any()) }
        coVerify(exactly = 0) { recentReactionsStore.record(any()) }
    }

    @Test
    fun `a second tap while the first call is in flight coalesces into one follow-up call`() = runTest {
        // The add call never completes within this test body — it's an uncompleted deferred result —
        // so the second, opposite tap must be recorded rather than sent immediately.
        val addDeferred = CompletableDeferred<Result<EmojiReaction>>()
        coEvery { messagingController.addReaction(chatId, 1L, Emoji(emoji)) } coAnswers { addDeferred.await() }
        coEvery { messagingController.removeReaction(chatId, 1L, Emoji(emoji)) } returns Result.success(
            EmojiReaction(emoji = Emoji(emoji), count = 0, selfReactor = null, sampleReactors = emptyList(), version = 2)
        )

        val firstTap = launch { delegate.toggleReaction(chatId, 1L, emoji) }
        yield()
        // Second tap: toggles back off while the ADD is still in flight — must not send REMOVE yet.
        delegate.toggleReaction(chatId, 1L, emoji)
        coVerify(exactly = 0) { messagingController.removeReaction(any(), any(), any()) }

        addDeferred.complete(
            Result.success(
                EmojiReaction(
                    emoji = Emoji(emoji),
                    count = 1,
                    selfReactor = Reactor(userId = selfId, reactedAt = Instant.fromEpochSeconds(1000), version = 1),
                    sampleReactors = emptyList(),
                    version = 1,
                )
            )
        )
        firstTap.join()

        // The coalesced REMOVE follow-up fires once the ADD settles.
        coVerify { messagingController.removeReaction(chatId, 1L, Emoji(emoji)) }
    }

    @Test
    fun `refresh merges server summaries into the persisted store and the overlay`() = runTest {
        val summary = ReactionSummary(
            messageId = 1L,
            reactions = listOf(
                EmojiReaction(
                    emoji = Emoji(emoji),
                    count = 3,
                    selfReactor = null,
                    sampleReactors = emptyList(),
                    version = 5,
                )
            ),
        )
        coEvery { messagingController.getReactionSummariesByIds(chatId, listOf(1L)) } returns
            Result.success(listOf(summary))

        val result = delegate.refreshReactions(chatId, listOf(1L))

        assertTrue(result.isSuccess)
        coVerify { messageDataSource.mergeReactions(chatId, 1L, any()) }
        val overlay = stateHolder.current.reactionOverlays[chatId]?.get(1L)
        assertEquals(3, overlay?.reactions?.first()?.count)
    }

    /**
     * After relaunch, a confirmed self-reaction lives only in Room — [ChatStateHolder]'s in-memory
     * overlay is empty. Tapping that emoji must still compute a REMOVE, not an ADD: `snapshot` has
     * to seed the fresh [com.flipcash.shared.chat.reactions.ReactionState] from the stored summary,
     * not just from the overlay.
     */
    @Test
    fun `toggling an emoji already reacted to in Room, with no overlay, sends a remove`() = runTest {
        coEvery { messageDataSource.getMessage(chatId, 1L) } returns ChatMessage(
            messageId = 1L,
            senderId = selfId,
            content = emptyList(),
            timestamp = Instant.fromEpochSeconds(1000),
            unreadSeq = 0,
            reactions = ReactionSummary(
                messageId = 1L,
                reactions = listOf(
                    EmojiReaction(
                        emoji = Emoji(emoji),
                        count = 1,
                        selfReactor = Reactor(userId = selfId, reactedAt = Instant.fromEpochSeconds(1000), version = 1),
                        sampleReactors = emptyList(),
                        version = 1,
                    )
                ),
            ),
        )
        coEvery { messagingController.removeReaction(chatId, 1L, Emoji(emoji)) } returns Result.success(
            EmojiReaction(emoji = Emoji(emoji), count = 0, selfReactor = null, sampleReactors = emptyList(), version = 2)
        )

        delegate.toggleReaction(chatId, 1L, emoji)

        coVerify { messagingController.removeReaction(chatId, 1L, Emoji(emoji)) }
        coVerify(exactly = 0) { messagingController.addReaction(any(), any(), any()) }
    }

    @Test
    fun `getReactorsPage passes through to the controller`() = runTest {
        coEvery { messagingController.getReactors(chatId, 1L, Emoji(emoji), any()) } returns
            Result.success(ReactorsPage(reactors = emptyList(), hasMore = false))

        val result = delegate.getReactorsPage(chatId, 1L, emoji)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull()?.nextToken)
    }
}
