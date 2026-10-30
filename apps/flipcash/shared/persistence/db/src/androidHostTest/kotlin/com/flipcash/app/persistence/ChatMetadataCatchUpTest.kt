package com.flipcash.app.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * A catch-up that learns nothing must leave the row byte-for-byte alone, because the chat list
 * reorders on `last_activity_epoch_ms` and rebuilds on any change to the row.
 */
@RunWith(RobolectricTestRunner::class)
class ChatMetadataCatchUpTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var dao: com.flipcash.app.persistence.dao.ChatMetadataDao

    @Before
    fun setUp() {
        FlipcashDatabase.init(context, "cccccccccccccccccccccccc")
        dao = FlipcashDatabase.requireInstance().chatMetadataDao()
        runBlocking {
            dao.upsert(
                ChatMetadataEntity(
                    chatIdHex = CHAT,
                    chatType = "CONTACT_DM",
                    lastActivityEpochMs = 5_000,
                    lastMessageId = 10,
                    latestEventSequence = 20,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        FlipcashDatabase.closeDb()
    }

    private fun row() = runBlocking { dao.observeAll().first().single() }

    @Test
    fun `the same newest message changes nothing, even with a later timestamp`() = runBlocking {
        val before = row()

        dao.applyCatchUp(CHAT, latestEventSequence = 20, messageId = 10, timestampEpochMs = 9_999)

        assertEquals(before, row())
    }

    @Test
    fun `an older message and an older cursor change nothing`() = runBlocking {
        val before = row()

        dao.applyCatchUp(CHAT, latestEventSequence = 3, messageId = 4, timestampEpochMs = 9_999)

        assertEquals(before, row())
    }

    @Test
    fun `a newer message moves the id and activity together with the cursor`() = runBlocking {
        dao.applyCatchUp(CHAT, latestEventSequence = 25, messageId = 11, timestampEpochMs = 6_000)

        val after = row()
        assertEquals(11L, after.lastMessageId)
        assertEquals(6_000L, after.lastActivityEpochMs)
        assertEquals(25L, after.latestEventSequence)
    }

    @Test
    fun `a newer message with an older timestamp never moves activity backwards`() = runBlocking {
        dao.applyCatchUp(CHAT, latestEventSequence = 0, messageId = 11, timestampEpochMs = 1_000)

        val after = row()
        assertEquals(11L, after.lastMessageId)
        assertEquals(5_000L, after.lastActivityEpochMs)
    }

    @Test
    fun `a cursor-only catch-up leaves the message fields alone`() = runBlocking {
        dao.applyCatchUp(CHAT, latestEventSequence = 30, messageId = 0, timestampEpochMs = 0)

        val after = row()
        assertEquals(30L, after.latestEventSequence)
        assertEquals(10L, after.lastMessageId)
        assertEquals(5_000L, after.lastActivityEpochMs)
    }

    private companion object {
        const val CHAT = "aabb"
    }
}
