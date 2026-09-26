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
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.RosterSummary
import com.flipcash.services.models.chat.ViewerState
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
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

    /**
     * `MessageContent.Encrypted` is a decode result kept verbatim (not decrypted) so it can be
     * persisted and later re-encoded faithfully -- see that type's doc. A byte-for-byte round
     * trip through the row proves the ciphertext isn't quietly discarded on the way through Room.
     */
    @Test
    fun `encrypted content survives the round trip through the message row`() {
        val encrypted = MessageContent.Encrypted(
            scheme = 1,
            nonce = byteArrayOf(0x01, 0x02, 0x03, -1, 0x00),
            ciphertext = byteArrayOf(-128, 0x7F, 0x10, 0x20, 0x30, 0x40),
        )
        val message = ChatMessage(
            messageId = 1,
            senderId = listOf(0xAB.toByte()),
            content = listOf(encrypted),
            timestamp = Instant.fromEpochSeconds(1_000),
            unreadSeq = 1,
        )

        val entity = mapper.toEntity(CHAT_HEX, message)
        val restored = mapper.toMessage(entity).content.single() as MessageContent.Encrypted

        assertEquals(1, restored.scheme)
        assertEquals(true, encrypted.nonce.contentEquals(restored.nonce))
        assertEquals(true, encrypted.ciphertext.contentEquals(restored.ciphertext))
    }

    private fun groupMetadata() = ChatMetadata(
        chatId = ChatId(CHAT_HEX),
        type = ChatType.GROUP,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(1_000),
        title = "Flipcash Staff",
        picture = null,
        rosterSummary = RosterSummary(memberCount = 12, version = 4),
        rules = ChatRules(
            listener = listOf(ChatRuleRequirement.Staff),
            speaker = listOf(
                ChatRuleRequirement.MinimumBalance(
                    amount = Fiat(quarks = 500, currencyCode = CurrencyCode.USD),
                    mints = emptyList(),
                )
            ),
        ),
    )

    @Test
    fun `group metadata writes its identity and roster onto the row`() {
        val entity = mapper.toEntity(groupMetadata())

        assertEquals("Flipcash Staff", entity.title)
        assertEquals(12L, entity.memberCount)
        assertEquals(4L, entity.rosterVersion)
        assertEquals(true, entity.isMember)
    }

    @Test
    fun `a chat written as a non-member is marked as one`() {
        val entity = mapper.toEntity(groupMetadata(), isMember = false)

        assertEquals(false, entity.isMember)
    }

    @Test
    fun `group rules survive the round trip`() {
        val entity = mapper.toEntity(groupMetadata())

        val restored = mapper.toMetadata(entity, members = emptyList(), lastMessage = null)

        assertEquals(listOf(ChatRuleRequirement.Staff), restored.rules?.listener)
        val speaker = restored.rules?.speaker?.single() as ChatRuleRequirement.MinimumBalance
        assertEquals(500L, speaker.amount.quarks)
        assertEquals(CurrencyCode.USD, speaker.amount.currencyCode)
    }

    @Test
    fun `group identity and roster survive the round trip`() {
        val entity = mapper.toEntity(groupMetadata())

        val restored = mapper.toMetadata(entity, members = emptyList(), lastMessage = null)

        assertEquals("Flipcash Staff", restored.title)
        assertEquals(12L, restored.rosterSummary.memberCount)
        assertEquals(4L, restored.rosterSummary.version)
    }

    @Test
    fun `a DM keeps a null title and an empty roster`() {
        val entity = mapper.toEntity(metadata(latestEventSequence = 0))

        assertEquals(null, entity.title)
        assertEquals(0L, entity.memberCount)
        assertEquals(0L, entity.rosterVersion)
        assertEquals(null, entity.rulesJson)
    }

    /**
     * `ChatMetadata.creator`/`useE2ee` are only ever set from the network today
     * ([toChatMetadata] in services/flipcash), but a chat rebuilt from Room goes through this
     * mapper's [ChatEntityMapper.toEntity]/[ChatEntityMapper.toMetadata] round trip -- if either
     * field were dropped there, a chat reloaded from the database would silently lose its group
     * creator or its transitional E2EE flag.
     */
    @Test
    fun `group creator and use_e2ee survive the round trip through the row`() {
        val metadata = groupMetadata().copy(
            creator = listOf(0xCD.toByte()),
            useE2ee = true,
        )

        val entity = mapper.toEntity(metadata)
        assertEquals("cd", entity.creatorHex)
        assertEquals(true, entity.useE2ee)

        val restored = mapper.toMetadata(entity, members = emptyList(), lastMessage = null)
        assertEquals(listOf(0xCD.toByte()), restored.creator)
        assertEquals(true, restored.useE2ee)
    }

    @Test
    fun `a chat with no creator and no e2ee round trips to null and false`() {
        val entity = mapper.toEntity(metadata(latestEventSequence = 0))

        assertEquals(null, entity.creatorHex)
        assertEquals(false, entity.useE2ee)

        val restored = mapper.toMetadata(entity, members = emptyList(), lastMessage = null)
        assertEquals(null, restored.creator)
        assertEquals(false, restored.useE2ee)
    }

    @Test
    fun `a timed mute is written as its deadline`() {
        val entity = mapper.toEntity(
            groupMetadata().copy(
                viewerState = ViewerState(
                    mute = MuteState.Until(Instant.fromEpochSeconds(2_000)),
                    version = 3,
                )
            )
        )

        assertEquals(2_000_000L, entity.muteUntilEpochMs)
        assertEquals(false, entity.muteForever)
        assertEquals(3L, entity.viewerStateVersion)
    }

    @Test
    fun `an indefinite mute is written as the flag and no deadline`() {
        val entity = mapper.toEntity(
            groupMetadata().copy(viewerState = ViewerState(mute = MuteState.Forever, version = 3))
        )

        assertEquals(null, entity.muteUntilEpochMs)
        assertEquals(true, entity.muteForever)
    }

    /**
     * The deadline survives even once it is in the past. Whether the mute is still in force is
     * `isActiveAt(now)`'s answer at render time, not something the mapper resolves on the way
     * out — resolving it here would make the row and the model disagree about what is stored.
     */
    @Test
    fun `a lapsed mute still round-trips as its deadline`() {
        val lapsed = Instant.fromEpochSeconds(1_500)
        val entity = mapper.toEntity(
            groupMetadata().copy(viewerState = ViewerState(mute = MuteState.Until(lapsed), version = 3))
        )

        val restored = mapper.toMetadata(entity, members = emptyList(), lastMessage = null)

        assertEquals(MuteState.Until(lapsed), restored.viewerState?.mute)
        assertEquals(3L, restored.viewerState?.version)
    }

    /**
     * Unlike `latestEventSequence` above, the version is carried back out: it is what the write
     * gate compares, so a metadata rebuilt from a row and pushed back through an upsert has to
     * fail the gate rather than re-apply itself over something newer.
     */
    @Test
    fun `the viewer state version survives the round trip`() {
        val entity = mapper.toEntity(
            groupMetadata().copy(viewerState = ViewerState(mute = MuteState.Forever, version = 9))
        )

        val restored = mapper.toMetadata(entity, members = emptyList(), lastMessage = null)

        assertEquals(9L, restored.viewerState?.version)
        assertEquals(MuteState.Forever, restored.viewerState?.mute)
    }

    @Test
    fun `a chat holding nothing about the viewer has no viewer state`() {
        val entity = mapper.toEntity(metadata(latestEventSequence = 0))

        val restored = mapper.toMetadata(entity, members = emptyList(), lastMessage = null)

        assertEquals(null, restored.viewerState)
    }

    @Test
    fun `an edit grant survives the round trip`() {
        val entity = mapper.toEntity(
            groupMetadata().copy(
                viewerState = ViewerState(
                    version = 3,
                    permissions = ViewerState.Permissions(canEdit = true),
                )
            )
        )
        assertEquals(true, entity.canEdit)

        val restored = mapper.toMetadata(entity, emptyList(), null)

        assertEquals(true, restored.viewerState?.permissions?.canEdit)
    }

    /**
     * A grant is state held about the viewer even when nothing else is: an unmuted chat at
     * version 0 that the server says is editable. Folding that row into a null viewer state
     * would deny an edit the server allows, so the emptiness test has to count the grant.
     */
    @Test
    fun `a chat holding only an edit grant still has viewer state`() {
        val entity = mapper.toEntity(
            groupMetadata().copy(
                viewerState = ViewerState(permissions = ViewerState.Permissions(canEdit = true))
            )
        )

        val restored = mapper.toMetadata(entity, emptyList(), null)

        assertEquals(true, restored.viewerState?.permissions?.canEdit)
    }

    // region Reactions

    @Test
    fun `null reactions round-trip through encode and decode as null`() {
        assertEquals(null, mapper.encodeReactions(null))
        assertEquals(null, mapper.decodeReactions(null))
    }

    @Test
    fun `a reaction summary round-trips through encode and decode`() {
        val summary = reactionSummary(emoji("👍") to 3L)

        val decoded = mapper.decodeReactions(mapper.encodeReactions(summary))

        assertEquals(summary, decoded)
    }

    @Test
    fun `merge keeps the stored entry when incoming omits its emoji`() {
        val stored = reactionSummary(emoji("👍") to 1L, emoji("❤️") to 1L)
        val incoming = reactionSummary(emoji("👍") to 2L)

        val merged = mapper.mergeReactions(stored, incoming)

        assertEquals(setOf("👍", "❤️"), merged.reactions.map { it.emoji.value }.toSet())
    }

    @Test
    fun `merge keeps the higher-version entry per emoji`() {
        val stored = reactionSummary(emoji("👍") to 5L)
        val incoming = reactionSummary(emoji("👍") to 2L)

        val merged = mapper.mergeReactions(stored, incoming)

        assertEquals(5L, merged.reactions.single().count)
    }

    @Test
    fun `merge onto null stored state returns the incoming summary unchanged`() {
        val incoming = reactionSummary(emoji("👍") to 1L)

        assertEquals(incoming, mapper.mergeReactions(null, incoming))
    }

    private fun emoji(value: String) = com.flipcash.services.models.chat.Emoji(value)

    /** Builds a [ReactionSummary] with one [EmojiReaction] per (emoji, version) pair, version doubling as count for easy assertions. */
    private fun reactionSummary(
        vararg entries: Pair<com.flipcash.services.models.chat.Emoji, Long>,
    ) = com.flipcash.services.models.chat.ReactionSummary(
        messageId = 42L,
        reactions = entries.map { (emoji, version) ->
            com.flipcash.services.models.chat.EmojiReaction(
                emoji = emoji,
                count = version,
                selfReactor = null,
                sampleReactors = emptyList(),
                version = version,
            )
        },
    )

    // endregion

    private companion object {
        const val CHAT_HEX = "aabbccdd"
    }
}
