package com.flipcash.app.tipping.internal

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.LobbyMember
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.solana.keys.PublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

/** What a hosted group's row draws while someone waits to join it, and where it sits. */
class JoinRequestRowTest {

    private fun waiting(enteredAtMs: Long) = LobbyMember(
        userProfile = UserProfile.Empty,
        publicKey = PublicKey("11111111111111111111111111111111"),
        enteredAt = Instant.fromEpochMilliseconds(enteredAtMs),
    )

    private val group = ConversationReference(
        chatId = ChatId(byteArrayOf(1)),
        isGroup = true,
        lastActivity = Instant.fromEpochMilliseconds(500),
        unreadCount = 3,
    )

    @Test
    fun `the pill counts unread messages and requests together`() {
        assertEquals(4, group.copy(joinRequests = listOf(waiting(100))).rowUnreadCount)
        assertEquals(3, group.rowUnreadCount)
    }

    @Test
    fun `an unknown unread count stays unknown with requests`() {
        assertNull(group.copy(unreadCount = null, joinRequests = listOf(waiting(100))).rowUnreadCount)
    }

    @Test
    fun `the row shows the later of its last activity and its newest request`() {
        assertEquals(Instant.fromEpochMilliseconds(900), group.copy(joinRequests = listOf(waiting(100), waiting(900))).rowActivity)
        assertEquals(Instant.fromEpochMilliseconds(500), group.copy(joinRequests = listOf(waiting(100))).rowActivity)
        assertEquals(Instant.fromEpochMilliseconds(100), group.copy(lastActivity = null, joinRequests = listOf(waiting(100))).rowActivity)
    }

    @Test
    fun `rows draw in the projection's order, not the feed's`() {
        val a = ConversationReference(chatId = ChatId(byteArrayOf(1)))
        val b = ConversationReference(chatId = ChatId(byteArrayOf(2)))
        val c = ConversationReference(chatId = ChatId(byteArrayOf(3)))

        val ordered = listOf(a, b, c).inOrderOf(listOf(c.chatId.toString(), a.chatId.toString(), b.chatId.toString()))

        assertEquals(listOf(c, a, b), ordered)
    }
}
