package com.flipcash.shared.chat

import com.flipcash.services.models.chat.Emoji
import com.flipcash.services.models.chat.EmojiReaction
import com.flipcash.services.models.chat.ReactionSummary
import com.flipcash.services.models.chat.Reactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Covers [MessageReactions.from] — the pure fallback the ViewModel applies to a [ChatMessage]'s
 * persisted `reactions` for a message [ReactionOperations.observeChatReactions] has no live
 * override for (see that function's KDoc for the full contract).
 */
class MessageReactionsTest {

    @Test
    fun `from builds pills from a persisted summary with no live overrides`() {
        val summary = ReactionSummary(
            messageId = 1L,
            reactions = listOf(
                EmojiReaction(
                    emoji = Emoji("👍"),
                    count = 2,
                    selfReactor = Reactor(
                        userId = listOf<Byte>(1),
                        reactedAt = Instant.fromEpochSeconds(10),
                        version = 1,
                    ),
                    sampleReactors = emptyList(),
                    version = 1,
                ),
            ),
        )

        val reactions = MessageReactions.from(summary)

        assertEquals(1, reactions.pills.size)
        assertEquals("👍", reactions.pills.first().emoji)
        assertEquals(2, reactions.pills.first().count)
        assertTrue(reactions.pills.first().selfReacted)
        assertEquals(listOf("👍"), reactions.selfReactions.map { it.emoji })
    }

    @Test
    fun `from a null summary is empty`() {
        val reactions = MessageReactions.from(null)

        assertTrue(reactions.pills.isEmpty())
        assertTrue(reactions.selfReactions.isEmpty())
    }

    @Test
    fun `from drops a tombstoned emoji`() {
        val summary = ReactionSummary(
            messageId = 1L,
            reactions = listOf(
                EmojiReaction(
                    emoji = Emoji("❤️"),
                    count = 0,
                    selfReactor = null,
                    sampleReactors = emptyList(),
                    version = 3,
                ),
            ),
        )

        val reactions = MessageReactions.from(summary)

        assertTrue(reactions.pills.isEmpty())
    }
}
