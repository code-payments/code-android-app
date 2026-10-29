package com.flipcash.app.persistence.sources.mapper.chat

import com.flipcash.services.models.chat.DeliveryStatus
import com.flipcash.app.persistence.entities.MessageStatus
import com.flipcash.app.persistence.entities.ChatMemberEntity
import com.flipcash.app.persistence.entities.ChatMemberWithProfile
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.WidgetContent
import com.flipcash.services.models.handle
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.PinSummary
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.RosterSummary
import com.flipcash.services.models.chat.SilenceState
import com.flipcash.services.models.chat.ViewerState
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey
import com.flipcash.app.persistence.entities.EncryptionState
import com.flipcash.services.chat.MessageEncryption
import com.flipcash.services.chat.UndecryptableReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `a row that never had a pin maps to no pin summary, and one with a version maps to it`() {
        val none = ChatMetadataEntity(
            chatIdHex = "ab", chatType = "GROUP", lastActivityEpochMs = 1, lastMessageId = null,
        )
        assertNull(mapper.toMetadata(none, emptyList(), null).pinSummary)

        val unpinned = mapper.toMetadata(none.copy(pinVersion = 3), emptyList(), null).pinSummary
        assertEquals(PinSummary(count = 0, version = 3), unpinned)
    }

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

    /** The 5b–5d copy names the inviter, so a stored join has to keep who invited them. */
    @Test
    fun `an invite join keeps its inviter through the round trip`() {
        val joined = MessageContent.System(
            fallbackText = "joined",
            event = MessageContent.SystemEvent.UserJoined(
                userId = listOf(0x01.toByte()),
                admittedBy = null,
                invitedBy = listOf(0x02.toByte()),
            ),
        )
        val entity = mapper.toEntity(
            CHAT_HEX,
            ChatMessage(
                messageId = 1,
                senderId = listOf(0xAB.toByte()),
                content = listOf(joined),
                timestamp = Instant.fromEpochSeconds(1_000),
                unreadSeq = 1,
            ),
        )

        assertEquals(joined, mapper.toMessage(entity).content.single())
    }

    @Test
    fun `a denied row maps to a denied delivery`() {
        val entity = mapper.toEntity(
            CHAT_HEX,
            ChatMessage(
                messageId = 1,
                senderId = listOf(0xAB.toByte()),
                content = listOf(MessageContent.Text("refused")),
                timestamp = Instant.fromEpochSeconds(1_000),
                unreadSeq = 1,
            ),
        ).copy(status = MessageStatus.DENIED)

        assertEquals(DeliveryStatus.DENIED, mapper.toMessage(entity).deliveryStatus)
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

    private fun cashMessage(tokenQuarks: Long?, receiver: List<Byte>? = null) = ChatMessage(
        messageId = 1,
        senderId = listOf(0xAB.toByte()),
        content = listOf(
            MessageContent.Cash(
                intentId = listOf<Byte>(1, 2, 3),
                amount = Fiat(quarks = 250_000L),
                mint = com.getcode.solana.keys.Mint.usdc,
                tokenQuarks = tokenQuarks,
                receiver = receiver,
            )
        ),
        timestamp = Instant.fromEpochSeconds(1_000),
        unreadSeq = 1,
    )

    @Test
    fun `cash token quarks survive the round trip without disturbing the fiat quarks`() {
        val restored = mapper.toMessage(mapper.toEntity(CHAT_HEX, cashMessage(tokenQuarks = 7_654_321L)))
            .content.single() as MessageContent.Cash

        assertEquals(7_654_321L, restored.tokenQuarks)
        assertEquals(250_000L, restored.amount.quarks)
    }

    @Test
    fun `cash with unknown token quarks round trips as null`() {
        val restored = mapper.toMessage(mapper.toEntity(CHAT_HEX, cashMessage(tokenQuarks = null)))
            .content.single() as MessageContent.Cash

        assertNull(restored.tokenQuarks)
    }

    @Test
    fun `cash multi-receiver parts and payment request id survive the round trip`() {
        val parts = listOf(
            MessageContent.Cash.Payment(listOf<Byte>(1), 1.25, 4L),
            MessageContent.Cash.Payment(listOf<Byte>(2), 1.25, 6L),
        )
        val requestId = List<Byte>(16) { (it + 1).toByte() }
        val message = cashMessage(tokenQuarks = 10L).let { m ->
            m.copy(content = m.content.map {
                (it as MessageContent.Cash).copy(multiReceiver = parts, paymentRequestId = requestId)
            })
        }

        val restored = mapper.toMessage(mapper.toEntity(CHAT_HEX, message))
            .content.single() as MessageContent.Cash

        assertEquals(parts, restored.multiReceiver)
        assertEquals(requestId, restored.paymentRequestId)
        assertNull(restored.receiver)
    }

    @Test
    fun `cash receiver survives the round trip, and its absence stays null`() {
        val receiver = listOf<Byte>(9, 8, 7)
        val with = mapper.toMessage(mapper.toEntity(CHAT_HEX, cashMessage(1L, receiver)))
            .content.single() as MessageContent.Cash
        val without = mapper.toMessage(mapper.toEntity(CHAT_HEX, cashMessage(1L)))
            .content.single() as MessageContent.Cash

        assertEquals(receiver, with.receiver)
        assertNull(without.receiver)
    }

    @Test
    fun `unsupported content survives the round trip through the message row`() {
        val message = ChatMessage(
            messageId = 1,
            senderId = listOf(0xAB.toByte()),
            content = listOf(MessageContent.Unsupported),
            timestamp = Instant.fromEpochSeconds(1_000),
            unreadSeq = 1,
        )

        val entity = mapper.toEntity(CHAT_HEX, message)

        assertEquals(listOf<MessageContent>(MessageContent.Unsupported), mapper.toMessage(entity).content)
    }

    @Test
    fun `an unknown widget survives the round trip through the message row as unsupported`() {
        val message = ChatMessage(
            messageId = 1,
            senderId = listOf(0xAB.toByte()),
            content = listOf(MessageContent.Widget(WidgetContent.Unsupported)),
            timestamp = Instant.fromEpochSeconds(1_000),
            unreadSeq = 1,
        )

        val entity = mapper.toEntity(CHAT_HEX, message)

        assertEquals(
            listOf<MessageContent>(MessageContent.Widget(WidgetContent.Unsupported)),
            mapper.toMessage(entity).content,
        )
    }

    @Test
    fun `a payment request widget survives the round trip through the message row`() {
        val widget = MessageContent.Widget(WidgetContent.PaymentRequest(List(32) { it.toByte() }))
        val message = ChatMessage(
            messageId = 1,
            senderId = listOf(0xAB.toByte()),
            content = listOf(widget),
            timestamp = Instant.fromEpochSeconds(1_000),
            unreadSeq = 1,
        )

        val entity = mapper.toEntity(CHAT_HEX, message)

        assertEquals(listOf<MessageContent>(widget), mapper.toMessage(entity).content)
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
    fun `creator, never and unsupported speaker rules survive the round trip`() {
        val metadata = groupMetadata().copy(
            rules = ChatRules(
                listener = emptyList(),
                speaker = listOf(
                    ChatRuleRequirement.Creator,
                    ChatRuleRequirement.Never,
                    ChatRuleRequirement.UnsupportedSpeakerRule,
                ),
            ),
        )

        val restored = mapper.toMetadata(mapper.toEntity(metadata), members = emptyList(), lastMessage = null)

        assertEquals(
            listOf(
                ChatRuleRequirement.Creator,
                ChatRuleRequirement.Never,
                ChatRuleRequirement.UnsupportedSpeakerRule,
            ),
            restored.rules?.speaker,
        )
    }

    /**
     * The write `MetadataUpdate.RulesChanged` and a rules edit make: [ChatEntityMapper.rulesSerialized]
     * into `rules_json`, read back with the row. A speaker minimum added and then removed has to
     * come back each time as it was sent, with the rules around it untouched.
     */
    @Test
    fun `a speaker minimum added and removed through a rules change survives the round trip`() {
        val mint = PublicKey(ByteArray(32) { 4 }.toList())
        val join = ChatRuleRequirement.MinimumBalance(Fiat(quarks = 1_000_000_000, currencyCode = CurrencyCode.JPY), listOf(mint))
        val chat = ChatRuleRequirement.MinimumBalance(Fiat(quarks = 2_500_000_000, currencyCode = CurrencyCode.JPY), listOf(mint))
        val withSpeaker = ChatRules(
            listener = listOf(join, ChatRuleRequirement.Staff),
            speaker = listOf(chat, ChatRuleRequirement.Creator),
        )
        val withoutSpeaker = withSpeaker.copy(speaker = listOf(ChatRuleRequirement.Creator))
        val entity = mapper.toEntity(groupMetadata())

        for (rules in listOf(withSpeaker, withoutSpeaker)) {
            val stored = entity.copy(rulesJson = mapper.rulesSerialized(rules))
            assertEquals(rules, mapper.toMetadata(stored, members = emptyList(), lastMessage = null).rules)
        }
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
    fun `a private group stays private through the row`() {
        val entity = mapper.toEntity(groupMetadata().copy(isPrivate = true))
        assertEquals(true, entity.isPrivate)

        val restored = mapper.toMetadata(entity, members = emptyList(), lastMessage = null)
        assertEquals(true, restored.isPrivate)
    }

    @Test
    fun `group description survives the round trip through the row`() {
        val entity = mapper.toEntity(groupMetadata().copy(description = "Weekly tips"))
        assertEquals("Weekly tips", entity.description)

        val restored = mapper.toMetadata(entity, members = emptyList(), lastMessage = null)
        assertEquals("Weekly tips", restored.description)
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

    @Test
    fun `an invite grant survives the round trip`() {
        val entity = mapper.toEntity(
            groupMetadata().copy(
                viewerState = ViewerState(permissions = ViewerState.Permissions(canInvite = true))
            )
        )
        assertEquals(true, entity.canInvite)

        val restored = mapper.toMetadata(entity, emptyList(), null)

        assertEquals(true, restored.viewerState?.permissions?.canInvite)
    }

    @Test
    fun `moderation grants survive the round trip`() {
        val entity = mapper.toEntity(
            groupMetadata().copy(
                viewerState = ViewerState(
                    version = 2,
                    permissions = ViewerState.Permissions(
                        moderation = ViewerState.ModerationPermissions(canSilence = true, canBan = true),
                    ),
                )
            )
        )
        assertEquals(true, entity.canSilence)
        assertEquals(true, entity.canBan)

        val moderation = mapper.toMetadata(entity, emptyList(), null).viewerState?.permissions?.moderation

        assertEquals(true, moderation?.canSilence)
        assertEquals(true, moderation?.canBan)
    }

    @Test
    fun `a timed silence survives the round trip as its deadline`() {
        val until = Instant.fromEpochMilliseconds(9_000)
        val entity = mapper.toEntity(
            groupMetadata().copy(
                viewerState = ViewerState(
                    version = 4,
                    restrictions = ViewerState.Restrictions(silence = SilenceState.Until(until)),
                )
            )
        )
        assertEquals(9_000L, entity.silenceUntilEpochMs)
        assertEquals(false, entity.silenceForever)

        val restored = mapper.toMetadata(entity, emptyList(), null)

        assertEquals(SilenceState.Until(until), restored.viewerState?.restrictions?.silence)
    }

    @Test
    fun `a forever silence survives the round trip`() {
        val entity = mapper.toEntity(
            groupMetadata().copy(
                viewerState = ViewerState(
                    version = 4,
                    restrictions = ViewerState.Restrictions(silence = SilenceState.Forever),
                )
            )
        )
        assertEquals(true, entity.silenceForever)
        assertEquals(null, entity.silenceUntilEpochMs)

        val restored = mapper.toMetadata(entity, emptyList(), null)

        assertEquals(SilenceState.Forever, restored.viewerState?.restrictions?.silence)
    }

    @Test
    fun `a chat with no silence reads back with no restrictions`() {
        val entity = mapper.toEntity(
            groupMetadata().copy(viewerState = ViewerState(version = 4))
        )

        assertEquals(null, mapper.toMetadata(entity, emptyList(), null).viewerState?.restrictions)
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

    // region encrypted messages

    private val sealed = MessageContent.Encrypted(scheme = 1, nonce = ByteArray(24) { 3 }, ciphertext = byteArrayOf(9, 8, 7))

    private fun encryptedMessage(content: MessageContent, encryption: MessageEncryption?) = ChatMessage(
        messageId = 5,
        senderId = List(16) { 2 },
        content = listOf(content),
        timestamp = Instant.fromEpochSeconds(1_000),
        unreadSeq = 0,
        encryption = encryption,
    )

    @Test
    fun `an opened message stores its plaintext with the ciphertext beside it`() {
        val message = encryptedMessage(MessageContent.Text("hi"), MessageEncryption.Decrypted(sealed))

        val entity = mapper.toEntity(CHAT_HEX, message)

        assertEquals(EncryptionState.DECRYPTED, entity.encryptionState)
        assertEquals(message, mapper.toMessage(entity))
    }

    @Test
    fun `each unopened state round-trips with its ciphertext as content`() {
        listOf(
            MessageEncryption.KeyPending,
            MessageEncryption.Undecryptable(UndecryptableReason.Unsupported),
            MessageEncryption.Undecryptable(UndecryptableReason.Authentication),
        ).forEach { encryption ->
            val message = encryptedMessage(sealed, encryption)

            val entity = mapper.toEntity(CHAT_HEX, message)

            assertEquals(1, entity.ciphertextJson?.size)
            assertEquals(message, mapper.toMessage(entity))
        }
    }

    @Test
    fun `a plaintext message has no encryption columns`() {
        val entity = mapper.toEntity(CHAT_HEX, encryptedMessage(MessageContent.Text("plain"), null))

        assertNull(entity.ciphertextJson)
        assertNull(entity.encryptionState)
    }

    // endregion

    private companion object {
        const val CHAT_HEX = "aabbccdd"
    }
}
