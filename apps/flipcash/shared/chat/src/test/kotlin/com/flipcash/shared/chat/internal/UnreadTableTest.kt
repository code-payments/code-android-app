package com.flipcash.shared.chat.internal

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.PointerType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The shared "does this chat show as unread" table, which iOS encodes too. Unread means the newest
 * visible message is past the viewer's READ pointer and someone else sent it. A group with no self
 * row is not unread, since its roster is paged; a DM with no self row reads its pointer as zero.
 */
class UnreadTableTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)

    private fun member(id: List<Byte>, readPointer: Long? = null) = ChatMember(
        userId = id,
        userProfile = UserProfile.Empty,
        pointers = listOfNotNull(
            readPointer?.let {
                MessagePointer(
                    type = PointerType.READ,
                    userId = id,
                    value = it,
                    timestamp = Instant.fromEpochSeconds(1_000),
                )
            },
        ),
    )

    private fun unread(
        newest: Long?,
        fromSelf: Boolean = false,
        selfPointer: Long?,
        type: ChatType,
    ): Boolean {
        val members = listOfNotNull(member(otherId), selfPointer?.let { member(selfId, it) })
        val lastMessage = newest?.let {
            ChatMessage(
                messageId = it,
                senderId = if (fromSelf) selfId else otherId,
                content = listOf(MessageContent.Text("m$it")),
                timestamp = Instant.fromEpochSeconds(it),
                unreadSeq = it,
            )
        }
        val metadata = ChatMetadata(
            chatId = ChatId("aabbccdd"),
            type = type,
            members = members,
            lastMessage = lastMessage,
            lastActivity = Instant.fromEpochSeconds(2_000),
        )
        return unreadCount(metadata, selfId) > 0
    }

    @Test
    fun `newest from someone else past the pointer`() =
        assertEquals(true, unread(newest = 5, selfPointer = 4, type = ChatType.CONTACT_DM))

    @Test
    fun `newest is your own`() =
        assertEquals(false, unread(newest = 5, fromSelf = true, selfPointer = 4, type = ChatType.CONTACT_DM))

    @Test
    fun `pointer at the newest`() =
        assertEquals(false, unread(newest = 5, selfPointer = 5, type = ChatType.GROUP))

    @Test
    fun `group with no self row`() =
        assertEquals(false, unread(newest = 5, selfPointer = null, type = ChatType.GROUP))

    @Test
    fun `DM with no self row reads the pointer as zero`() =
        assertEquals(true, unread(newest = 5, selfPointer = null, type = ChatType.CONTACT_DM))

    @Test
    fun `no messages`() =
        assertEquals(false, unread(newest = null, selfPointer = 4, type = ChatType.CONTACT_DM))
}
