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
 * The shared unread table, which iOS encodes too. Unread means the newest visible message is past
 * the viewer's READ pointer and someone else sent it. A group with no self row is not unread, since
 * its roster is paged; a DM with no self row has read nothing. The count is the newest message's
 * unreadSeq less the stamp on the pointer's message, and null when that stamp can't be known.
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
    ): Boolean = count(newest, fromSelf, selfPointer, type) != 0

    /** [newestSeq] and [stamps] default to each message's stamp equalling its id. */
    private fun count(
        newest: Long?,
        fromSelf: Boolean = false,
        selfPointer: Long?,
        type: ChatType,
        newestSeq: Long? = newest,
        stamps: (Long) -> Long? = { it },
    ): Int? {
        val members = listOfNotNull(member(otherId), selfPointer?.let { member(selfId, it) })
        val lastMessage = newest?.let {
            ChatMessage(
                messageId = it,
                senderId = if (fromSelf) selfId else otherId,
                content = listOf(MessageContent.Text("m$it")),
                timestamp = Instant.fromEpochSeconds(it),
                unreadSeq = newestSeq ?: it,
            )
        }
        val metadata = ChatMetadata(
            chatId = ChatId("aabbccdd"),
            type = type,
            members = members,
            lastMessage = lastMessage,
            lastActivity = Instant.fromEpochSeconds(2_000),
        )
        return unreadCount(metadata, selfId, stamps)
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

    @Test
    fun `counts the messages between the pointer and the newest`() =
        assertEquals(3, count(newest = 9, selfPointer = 4, newestSeq = 7, stamps = { 4 }, type = ChatType.GROUP))

    @Test
    fun `no pointer counts every eligible message`() =
        assertEquals(5, count(newest = 8, selfPointer = null, newestSeq = 5, type = ChatType.CONTACT_DM))

    @Test
    fun `pointer's message not stored is unread by an unknown count`() =
        assertEquals(null, count(newest = 9, selfPointer = 4, stamps = { null }, type = ChatType.CONTACT_DM))

    @Test
    fun `newest not unread-eligible is unread by an unknown count`() =
        assertEquals(null, count(newest = 9, selfPointer = 4, newestSeq = 4, stamps = { 4 }, type = ChatType.CONTACT_DM))

    @Test
    fun `read chats count zero whatever the stamps say`() =
        assertEquals(0, count(newest = 5, selfPointer = 5, stamps = { null }, type = ChatType.CONTACT_DM))
}
