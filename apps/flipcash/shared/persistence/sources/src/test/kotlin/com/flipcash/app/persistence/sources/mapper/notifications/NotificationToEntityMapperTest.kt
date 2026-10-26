package com.flipcash.app.persistence.sources.mapper.notifications

import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.feed.MessageSubstitution
import com.flipcash.services.models.BoostedReaction
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Emoji
import com.flipcash.services.models.ActivityFeedNotification
import com.flipcash.services.models.NotificationMetadata
import com.flipcash.services.models.NotificationState
import com.flipcash.services.models.Substitution
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationToEntityMapperTest {

    private val mapper = SingleNotificationToEntityMapper(MetadataMapper())

    private fun notification(
        text: String,
        substitutions: List<Substitution> = emptyList(),
    ) = ActivityFeedNotification(
        id = listOf<Byte>(0x01, 0x02, 0x03),
        text = text,
        amount = null,
        timestamp = Instant.fromEpochSeconds(1700000000L),
        state = NotificationState.COMPLETED,
        metadata = NotificationMetadata.Unknown,
        textSubstitutions = substitutions,
    )

    @Test
    fun `persists the raw title template and the serialized substitutions`() {
        val userId: ID = listOf<Byte>(0x0A, 0x0B)
        val entity = mapper.map(
            notification(
                text = "Tipped {0}",
                substitutions = listOf(Substitution.UserId(fallback = "Sally", userId = userId)),
            )
        )

        // The template is stored raw — resolution happens live at display.
        assertEquals("Tipped {0}", entity.text)
        // Substitutions round-trip through the persisted JSON into core MessageSubstitutions.
        assertEquals(
            listOf(MessageSubstitution.UserId(fallback = "Sally", userId = userId)),
            MessageSubstitution.listFrom(entity.textSubstitutions),
        )
    }

    @Test
    fun `maps a phone substitution`() {
        val entity = mapper.map(
            notification(
                text = "{0} paid you",
                substitutions = listOf(Substitution.Phone(fallback = "Bob", phoneNumber = "+15551234567")),
            )
        )
        assertEquals(
            listOf(MessageSubstitution.Phone(fallback = "Bob", phoneNumber = "+15551234567")),
            MessageSubstitution.listFrom(entity.textSubstitutions),
        )
    }

    @Test
    fun `no substitutions leaves the column null`() {
        val entity = mapper.map(notification(text = "Deposited \$30.00"))
        assertEquals("Deposited \$30.00", entity.text)
        assertNull(entity.textSubstitutions)
    }

    // --- Boosted reaction persistence: the field rides the `metadata` JSON column. ---

    private val boost = BoostedReaction(
        chatId = ChatId(ByteArray(32) { (it + 5).toByte() }),
        messageId = 42L,
        emoji = Emoji("\uD83D\uDD25"),
    )

    private fun notification(metadata: NotificationMetadata) = ActivityFeedNotification(
        id = listOf<Byte>(0x01, 0x02, 0x03),
        text = "Sent",
        amount = null,
        timestamp = Instant.fromEpochSeconds(1700000000L),
        state = NotificationState.COMPLETED,
        metadata = metadata,
        textSubstitutions = emptyList(),
    )

    private fun roundTrip(metadata: NotificationMetadata): MessageMetadata? =
        MessageEntityToFeedMessageMapper().map(mapper.map(notification(metadata))).metadata

    @Test
    fun `P1 a sent boost reads back with the same chat, message and emoji`() {
        val read = roundTrip(
            NotificationMetadata.DirectlySentCrypto(userId = listOf<Byte>(0x0A), boostedReaction = boost)
        ) as MessageMetadata.DirectlySentCrypto

        assertEquals(boost, read.boostedReaction)
        assertEquals(boost.chatId, read.boostedReaction?.chatId)
        assertEquals(42L, read.boostedReaction?.messageId)
        assertEquals(Emoji("\uD83D\uDD25"), read.boostedReaction?.emoji)
    }

    @Test
    fun `P1 a received boost reads back with the same chat, message and emoji`() {
        val read = roundTrip(
            NotificationMetadata.ReceivedCrypto(userId = listOf<Byte>(0x0A), boostedReaction = boost)
        ) as MessageMetadata.ReceivedCrypto

        assertEquals(boost, read.boostedReaction)
    }

    @Test
    fun `P1 a split boost keeps its boost without a counterparty`() {
        val read = roundTrip(NotificationMetadata.DirectlySentCrypto(boostedReaction = boost))
            as MessageMetadata.DirectlySentCrypto

        assertNull(read.userId)
        assertEquals(boost, read.boostedReaction)
    }

    @Test
    fun `P2 a payment without a boost reads back null`() {
        val sent = roundTrip(NotificationMetadata.DirectlySentCrypto(userId = listOf<Byte>(0x0A)))
            as MessageMetadata.DirectlySentCrypto
        val received = roundTrip(NotificationMetadata.ReceivedCrypto(userId = listOf<Byte>(0x0A)))
            as MessageMetadata.ReceivedCrypto

        assertNull(sent.boostedReaction)
        assertNull(received.boostedReaction)
    }

    @Test
    fun `boost rows keep the class names MessageDao filters on`() {
        val json = mapper.map(
            notification(NotificationMetadata.ReceivedCrypto(boostedReaction = boost))
        ).metadata
        assertNotNull(json)
        assertEquals(true, json!!.contains("com.flipcash.app.core.feed.MessageMetadata.ReceivedCrypto"))
    }

    @Test
    fun `P3 metadata JSON written before the field existed decodes with no boost`() {
        val sentBefore =
            """{"type":"com.flipcash.app.core.feed.MessageMetadata.DirectlySentCrypto","userId":[10,11,12]}"""
        val receivedBefore =
            """{"type":"com.flipcash.app.core.feed.MessageMetadata.ReceivedCrypto","phoneNumber":"+15551234567"}"""

        val sent = MessageMetadata.from(sentBefore) as MessageMetadata.DirectlySentCrypto
        val received = MessageMetadata.from(receivedBefore) as MessageMetadata.ReceivedCrypto

        assertEquals(listOf<Byte>(10, 11, 12), sent.userId)
        assertNull(sent.boostedReaction)
        assertEquals("+15551234567", received.phoneNumber)
        assertNull(received.boostedReaction)
    }
}
