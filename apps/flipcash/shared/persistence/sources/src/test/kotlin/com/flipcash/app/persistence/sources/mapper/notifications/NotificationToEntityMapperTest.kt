package com.flipcash.app.persistence.sources.mapper.notifications

import com.flipcash.app.core.feed.MessageSubstitution
import com.flipcash.services.models.ActivityFeedNotification
import com.flipcash.services.models.NotificationMetadata
import com.flipcash.services.models.NotificationState
import com.flipcash.services.models.Substitution
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant
import org.junit.Assert.assertEquals
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
}
