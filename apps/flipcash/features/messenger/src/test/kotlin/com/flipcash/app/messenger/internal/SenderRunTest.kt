package com.flipcash.app.messenger.internal

import com.flipcash.app.messenger.internal.screens.components.startsSenderRun
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.SenderIdentity
import com.flipcash.services.models.chat.MessageContent
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Which bubble in a run wears the sender's name and picture.
 *
 * The list is `reverseLayout`, so index 0 is the newest message at the bottom and index + 1 is the
 * message drawn *above*. A run therefore starts at its oldest message — the topmost one, which is
 * the one that carries the attribution.
 */
class SenderRunTest {

    private val alice = SenderIdentity(userId = listOf<Byte>(1), displayName = "Alice", picture = null)
    private val bob = SenderIdentity(userId = listOf<Byte>(2), displayName = "Bob", picture = null)

    private fun bubble(
        sender: SenderIdentity?,
        isFromSelf: Boolean = false,
        senderId: List<Byte>? = null,
    ) = ChatListItem.ContentBubble(
        messageId = 1L,
        contentIndex = 0,
        content = MessageContent.Text("hi"),
        isFromSelf = isFromSelf,
        timestamp = Instant.fromEpochMilliseconds(1_000),
        sender = sender,
        senderId = senderId,
    )

    @Test
    fun `a bubble with no sender never starts a run`() {
        assertFalse(startsSenderRun(bubble(sender = null), older = null))
        assertFalse(startsSenderRun(bubble(sender = null, isFromSelf = true), older = bubble(alice)))
    }

    @Test
    fun `the oldest loaded bubble starts a run`() {
        assertTrue(startsSenderRun(bubble(alice), older = null))
    }

    @Test
    fun `a bubble under a different sender starts a run`() {
        assertTrue(startsSenderRun(bubble(alice), older = bubble(bob)))
    }

    @Test
    fun `a bubble under the same sender continues the run`() {
        assertFalse(startsSenderRun(bubble(alice), older = bubble(alice)))
    }

    @Test
    fun `a bubble under one of the viewer's own starts a run`() {
        assertTrue(startsSenderRun(bubble(alice), older = bubble(sender = null, isFromSelf = true)))
    }

    @Test
    fun `a bubble under a date separator starts a run`() {
        val separator = ChatListItem.DateSeparator(Instant.fromEpochMilliseconds(1_000))
        assertTrue(startsSenderRun(bubble(alice), older = separator))
    }

    @Test
    fun `an unresolved profile still breaks the run at its sender`() {
        // A group's profile map lands after the first page, so `sender` is null on every member's
        // bubble to begin with. The id is what has to tell them apart until then.
        val unnamedAlice = bubble(sender = null, senderId = alice.userId)
        val unnamedBob = bubble(sender = null, senderId = bob.userId)
        assertTrue(startsSenderRun(unnamedAlice, older = unnamedBob))
        assertFalse(startsSenderRun(unnamedAlice, older = unnamedAlice))
    }
}
