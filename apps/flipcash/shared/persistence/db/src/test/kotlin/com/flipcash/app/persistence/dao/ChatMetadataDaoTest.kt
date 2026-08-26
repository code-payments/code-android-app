package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Covers what a feed sync is allowed to overwrite. `latest_event_sequence` (the applied
 * catch-up cursor) and `analytics_counted_through` (the received-message replay guard) are
 * client-owned watermarks that no server payload carries, so an upsert of server truth must
 * leave them alone — a whole-row replace would rewind both.
 */
@RunWith(RobolectricTestRunner::class)
class ChatMetadataDaoTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var dao: ChatMetadataDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.chatMetadataDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        chatIdHex: String = CHAT_HEX,
        chatType: String = "CONTACT_DM",
        lastActivityEpochMs: Long = 1_000,
        lastMessageId: Long? = null,
        latestEventSequence: Long = 0,
        isHidden: Boolean = false,
        analyticsCountedThrough: Long = 0,
    ) = ChatMetadataEntity(
        chatIdHex = chatIdHex,
        chatType = chatType,
        lastActivityEpochMs = lastActivityEpochMs,
        lastMessageId = lastMessageId,
        latestEventSequence = latestEventSequence,
        isHidden = isHidden,
        analyticsCountedThrough = analyticsCountedThrough,
    )

    @Test
    fun `upsert preserves the client-owned watermarks`() = runTest {
        dao.upsert(entity())
        dao.updateLatestEventSequence(CHAT_HEX, 9)
        dao.advanceAnalyticsCountedThrough(CHAT_HEX, 42)

        // A later feed sync carries neither watermark — the mapper never sets them.
        dao.upsert(entity(lastActivityEpochMs = 2_000, lastMessageId = 77))

        assertEquals(9L, dao.getLatestEventSequence(CHAT_HEX))
        assertEquals(42L, dao.getAnalyticsCountedThrough(CHAT_HEX))
    }

    @Test
    fun `upsert refreshes the server-owned columns`() = runTest {
        dao.upsert(entity(chatType = "CONTACT_DM", lastActivityEpochMs = 1_000, lastMessageId = 1))

        dao.upsert(
            entity(
                chatType = "TIP_DM",
                lastActivityEpochMs = 2_000,
                lastMessageId = 77,
                isHidden = true,
            )
        )

        val stored = requireNotNull(dao.getById(CHAT_HEX))
        assertEquals("TIP_DM", stored.chatType)
        assertEquals(2_000L, stored.lastActivityEpochMs)
        assertEquals(77L, stored.lastMessageId)
        assertEquals(true, stored.isHidden)
    }

    @Test
    fun `upsert inserts a chat the database has not seen`() = runTest {
        dao.upsert(entity(lastMessageId = 5))

        val stored = requireNotNull(dao.getById(CHAT_HEX))
        assertEquals(5L, stored.lastMessageId)
        assertEquals(0L, stored.latestEventSequence)
    }

    @Test
    fun `list upsert preserves each row's cursor`() = runTest {
        dao.upsert(listOf(entity(chatIdHex = CHAT_HEX), entity(chatIdHex = OTHER_HEX)))
        dao.updateLatestEventSequence(CHAT_HEX, 9)
        dao.updateLatestEventSequence(OTHER_HEX, 4)

        dao.upsert(
            listOf(
                entity(chatIdHex = CHAT_HEX, lastActivityEpochMs = 2_000),
                entity(chatIdHex = OTHER_HEX, lastActivityEpochMs = 3_000),
            )
        )

        assertEquals(9L, dao.getLatestEventSequence(CHAT_HEX))
        assertEquals(4L, dao.getLatestEventSequence(OTHER_HEX))
    }

    private companion object {
        const val CHAT_HEX = "aabbccdd"
        const val OTHER_HEX = "eeff0011"
    }
}
