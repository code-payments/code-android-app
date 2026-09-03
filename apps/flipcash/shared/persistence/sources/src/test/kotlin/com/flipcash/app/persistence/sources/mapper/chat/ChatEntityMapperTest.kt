package com.flipcash.app.persistence.sources.mapper.chat

import com.flipcash.app.persistence.entities.ChatMemberEntity
import com.flipcash.app.persistence.entities.ChatMemberWithProfile
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.handle
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

/**
 * `ChatMetadata.latestEventSequence` is the server's head at fetch time; the entity column of
 * the same name is the cursor the client has actually applied. The mapper must not conflate
 * them — carrying the head into the row would mark an unfetched transcript as caught up.
 */
class ChatEntityMapperTest {

    private val mapper = ChatEntityMapper()

    private fun metadata(latestEventSequence: Long) = ChatMetadata(
        chatId = ChatId(CHAT_HEX),
        type = ChatType.CONTACT_DM,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(1_000),
        latestEventSequence = latestEventSequence,
    )

    @Test
    fun `server head is not written as the applied cursor`() {
        val entity = mapper.toEntity(metadata(latestEventSequence = 12))

        assertEquals(0L, entity.latestEventSequence)
    }

    @Test
    fun `mapped entity carries the server-owned fields`() {
        val entity = mapper.toEntity(metadata(latestEventSequence = 12))

        assertEquals(CHAT_HEX, entity.chatIdHex)
        assertEquals(ChatType.CONTACT_DM.name, entity.chatType)
        assertEquals(1_000_000L, entity.lastActivityEpochMs)
    }

    @Test
    fun `chat rebuilt from the database reports an unknown server head`() {
        val entity = ChatMetadataEntity(
            chatIdHex = CHAT_HEX,
            chatType = ChatType.CONTACT_DM.name,
            lastActivityEpochMs = 1_000_000,
            lastMessageId = null,
            latestEventSequence = 9,
        )

        val metadata = mapper.toMetadata(entity, members = emptyList(), lastMessage = null)

        assertEquals(0L, metadata.latestEventSequence)
    }

    /**
     * The handle a tip DM falls back to when its counterparty never set a name only reaches the UI
     * through this cache — both the feed and the open conversation read members from Room, not from
     * the wire response. Dropping the username here made [UserProfile.handle] null everywhere.
     */
    @Test
    fun `a member's username survives the round trip through the profile row`() {
        val member = ChatMember(
            userId = listOf(0xAB.toByte()),
            userProfile = UserProfile.Empty.copy(displayName = "", username = "sally_streamer"),
            pointers = emptyList(),
        )

        val profileRow = mapper.toProfileEntity(member)
        assertEquals("sally_streamer", profileRow.username)

        val readBack = mapper.toMember(
            ChatMemberWithProfile(
                member = ChatMemberEntity(
                    chatIdHex = CHAT_HEX,
                    userIdHex = profileRow.userIdHex,
                    pointersJson = null,
                ),
                profile = profileRow,
            )
        )

        assertEquals("@sally_streamer", readBack.userProfile.handle)
    }

    /**
     * `is_deleted` is what the conversation list's "newest message that still has content" query
     * filters on, and the mapper is the only thing that ever writes it. If a tombstone were stored
     * with the flag clear, the list would preview "Message deleted" again.
     */
    @Test
    fun `a tombstone is flagged deleted and a text message is not`() {
        fun entity(content: MessageContent) = mapper.toEntity(
            CHAT_HEX,
            ChatMessage(
                messageId = 1,
                senderId = listOf(0xAB.toByte()),
                content = listOf(content),
                timestamp = Instant.fromEpochSeconds(1_000),
                unreadSeq = 1,
            ),
        )

        assertEquals(
            true,
            entity(MessageContent.Deleted(deletedTs = Instant.fromEpochSeconds(2_000), deletedBy = listOf(0xAB.toByte()))).isDeleted,
        )
        assertEquals(false, entity(MessageContent.Text("still here")).isDeleted)
    }

    private companion object {
        const val CHAT_HEX = "aabbccdd"
    }
}
