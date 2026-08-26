package com.flipcash.app.persistence.sources.mapper.chat

import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.services.models.chat.ChatId
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

    private companion object {
        const val CHAT_HEX = "aabbccdd"
    }
}
