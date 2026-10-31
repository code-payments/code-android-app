package com.flipcash.shared.chat

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.LobbyMember
import com.getcode.solana.keys.PublicKey
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A hosted private group with someone waiting to join reads as unread on the Chats list: it sorts
 * by its newest request, counts toward the Unread chip and the Archived row, and the badge.
 */
class ChatListJoinRequestsTest {

    private fun entry(
        id: String,
        lastActivityMs: Long,
        unread: Int? = 0,
        archived: Boolean = false,
        muted: Boolean = false,
        pendingRequests: Int = 0,
        newestRequestMs: Long = 0,
    ) = ChatListEntry(
        id = id,
        isGroup = true,
        lastActivityMs = lastActivityMs,
        archived = archived,
        muted = muted,
        hidden = false,
        unread = unread,
        pendingRequests = pendingRequests,
        newestRequestMs = newestRequestMs,
    )

    @Test
    fun `a request newer than every message moves the row to the top`() {
        val projection = projectChatList(
            listOf(
                entry("recent", lastActivityMs = 500),
                entry("hosted", lastActivityMs = 100, pendingRequests = 1, newestRequestMs = 900),
            )
        )

        assertEquals(listOf("hosted", "recent"), projection.main)
    }

    @Test
    fun `a request older than the last message leaves the row where its message put it`() {
        val projection = projectChatList(
            listOf(
                entry("recent", lastActivityMs = 500),
                entry("hosted", lastActivityMs = 300, pendingRequests = 1, newestRequestMs = 200),
            )
        )

        assertEquals(listOf("recent", "hosted"), projection.main)
    }

    @Test
    fun `requests alone put the row in the Unread chip and the badge`() {
        val projection = projectChatList(
            listOf(
                entry("read", lastActivityMs = 500),
                entry("hosted", lastActivityMs = 100, pendingRequests = 2, newestRequestMs = 200),
            )
        )

        assertEquals(listOf("hosted"), projection.unreadChip)
        assertEquals(listOf("hosted"), projection.idsFor(ChatListFilter.Unread))
        assertEquals(1, projection.tabBadge)
    }

    @Test
    fun `requests and unread messages count the row once`() {
        val projection = projectChatList(
            listOf(entry("hosted", lastActivityMs = 100, unread = 3, pendingRequests = 1, newestRequestMs = 200))
        )

        assertEquals(1, projection.unreadChip.size)
        assertEquals(1, projection.tabBadge)
    }

    @Test
    fun `an archived hosted group stays archived and its requests count on the Archived row`() {
        val projection = projectChatList(
            listOf(
                entry("main", lastActivityMs = 500),
                entry("hosted", lastActivityMs = 100, archived = true, pendingRequests = 1, newestRequestMs = 900),
            )
        )

        assertEquals(listOf("main"), projection.main)
        assertEquals(listOf("hosted"), projection.archived)
        assertTrue(projection.unreadChip.isEmpty())
        assertEquals(1, projection.archivedRowCount)
        assertEquals(0, projection.tabBadge)
    }

    @Test
    fun `no requests leaves a read row read`() {
        assertFalse(entry("hosted", lastActivityMs = 100).isUnread)
    }

    private val self = listOf<Byte>(1)
    private val other = listOf<Byte>(2)

    private fun group(creator: List<Byte>?, isPrivate: Boolean) = ChatMetadata(
        chatId = ChatId("aa"),
        type = ChatType.GROUP,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(0),
        creator = creator,
        isPrivate = isPrivate,
    )

    private val waiting = mapOf(
        ChatId("aa") to listOf(
            LobbyMember(
                userProfile = UserProfile.Empty,
                publicKey = PublicKey("11111111111111111111111111111111"),
                enteredAt = Instant.fromEpochSeconds(1),
            )
        )
    )

    @Test
    fun `only the host of a private group sees who waits`() {
        assertEquals(1, waiting.waitingFor(group(creator = self, isPrivate = true), self).size)
        assertTrue(waiting.waitingFor(group(creator = other, isPrivate = true), self).isEmpty())
        assertTrue(waiting.waitingFor(group(creator = self, isPrivate = false), self).isEmpty())
        assertTrue(waiting.waitingFor(group(creator = self, isPrivate = true), null).isEmpty())
    }
}
