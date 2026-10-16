package com.flipcash.app.messenger.internal

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.Emoji
import com.flipcash.services.models.chat.EmojiReaction
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.ReactionSummary
import com.getcode.opencode.model.core.bytes
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class GatedLandingTest {

    private val senderId = UUID.randomUUID().bytes

    private fun profile(name: String) = UserProfile(
        displayName = name,
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
        joinedAt = null,
        userId = null,
        username = null,
    )

    private fun message(
        content: MessageContent = MessageContent.Text("xxxx xxx xxxxx"),
        redacted: Boolean = true,
        reactions: ReactionSummary? = ReactionSummary(
            messageId = 42,
            reactions = listOf(
                EmojiReaction(Emoji("🔥"), count = 3, selfReactor = null, sampleReactors = emptyList(), version = 1),
                EmojiReaction(Emoji("👀"), count = 1, selfReactor = null, sampleReactors = emptyList(), version = 1),
            ),
        ),
    ) = ChatMessage(
        messageId = 42,
        senderId = senderId,
        content = listOf(content),
        timestamp = Instant.fromEpochSeconds(1_760_000_000),
        unreadSeq = 0,
        reactions = reactions,
        redacted = redacted,
    )

    @Test
    fun `a redacted message lands as one display-only row with its sender and reactions`() {
        val item = assertNotNull(gatedLandingBubble(message(), profile("Ada"), unnamed = "Unnamed"))

        assertEquals(42, item.messageId)
        assertTrue(item.redacted)
        assertEquals("Ada", item.sender?.displayName)
        assertEquals(senderId, item.senderId)
        assertEquals(listOf("🔥" to 3L, "👀" to 1L), item.reactionPills.map { it.emoji to it.count })
        assertTrue(item.capabilities.isEmpty())
        assertFalse(item.canReact)
        assertNull(item.linkCard)
    }

    @Test
    fun `the row draws before the sender's profile resolves, unnamed until it does`() {
        val item = assertNotNull(gatedLandingBubble(message(), senderProfile = null, unnamed = "Unnamed"))

        assertNull(item.sender)
        assertEquals(senderId, item.senderId)
    }

    @Test
    fun `a sender with no name or handle reads as unnamed`() {
        val item = assertNotNull(gatedLandingBubble(message(), profile(""), unnamed = "Unnamed"))

        assertEquals("Unnamed", item.sender?.displayName)
    }

    @Test
    fun `a message the server did not redact is not marked redacted`() {
        val item = assertNotNull(gatedLandingBubble(message(redacted = false), profile("Ada"), "Unnamed"))

        assertFalse(item.redacted)
        assertTrue(item.capabilities.isEmpty())
    }

    @Test
    fun `a deleted message draws nothing`() {
        assertNull(gatedLandingBubble(message(content = MessageContent.Deleted(Instant.fromEpochSeconds(1_760_000_100), deletedBy = null)), profile("Ada"), "Unnamed"))
    }
}
