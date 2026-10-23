package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.sources.mapper.notifications.MessageEntityToFeedMessageMapper
import com.flipcash.app.persistence.sources.mapper.notifications.MetadataMapper
import com.flipcash.app.persistence.sources.mapper.notifications.NotificationToEntityMapper
import com.flipcash.app.persistence.sources.mapper.notifications.SingleNotificationToEntityMapper
import com.flipcash.services.models.ActivityFeedNotification
import com.flipcash.services.models.NotificationMetadata
import com.flipcash.services.models.NotificationState
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The per-user DB is created at login, so a write can always be attempted before it exists. Skipping
 * the write in that window used to lose the page permanently: every sync path walks *forward* from
 * the newest cached id, so notifications that never landed are never requested again.
 */
class MessageDataSourceTest {

    private val dataSource = MessageDataSource(
        messageEntityMapper = MessageEntityToFeedMessageMapper(),
        notificationEntityMapper = NotificationToEntityMapper(
            SingleNotificationToEntityMapper(MetadataMapper())
        ),
    )

    @Test
    fun `upsert fails loudly instead of dropping the page when the database is not open`() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { dataSource.upsert(listOf(notification())) }
        }
    }

    private fun notification() = ActivityFeedNotification(
        id = listOf<Byte>(0x01, 0x02, 0x03),
        text = "Converted",
        amount = null,
        timestamp = Instant.fromEpochSeconds(1700000000L),
        state = NotificationState.COMPLETED,
        metadata = NotificationMetadata.Unknown,
        textSubstitutions = emptyList(),
    )
}
