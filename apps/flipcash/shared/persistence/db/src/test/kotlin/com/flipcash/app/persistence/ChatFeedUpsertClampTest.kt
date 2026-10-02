package com.flipcash.app.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.dao.ChatMetadataDao
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** A delayed feed response must not roll back a row a newer stream event already advanced. */
@RunWith(RobolectricTestRunner::class)
class ChatFeedUpsertClampTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var dao: ChatMetadataDao

    private fun feedRow(activity: Long, messageId: Long?, title: String? = null) = ChatMetadataEntity(
        chatIdHex = CHAT,
        chatType = "CONTACT_DM",
        lastActivityEpochMs = activity,
        lastMessageId = messageId,
        title = title,
    )

    @Before
    fun setUp() {
        FlipcashDatabase.init(context, "dddddddddddddddddddddddd")
        dao = FlipcashDatabase.requireInstance().chatMetadataDao()
        runBlocking { dao.upsert(feedRow(5_000, 10)) }
    }

    @After
    fun tearDown() = FlipcashDatabase.closeDb()

    private fun row() = runBlocking { dao.observeAll().first().single() }

    @Test
    fun `an older feed response after a stream event leaves the row unchanged`() = runBlocking {
        dao.applyCatchUp(CHAT, latestEventSequence = 30, messageId = 12, timestampEpochMs = 8_000)
        val advanced = row()

        dao.upsert(feedRow(activity = 5_000, messageId = 10))

        assertEquals(advanced, row())
    }

    @Test
    fun `a newer feed response advances activity and message id`() = runBlocking {
        dao.upsert(feedRow(activity = 9_000, messageId = 14))

        assertEquals(9_000L, row().lastActivityEpochMs)
        assertEquals(14L, row().lastMessageId)
    }

    @Test
    fun `other server-owned fields still take the feed's value and the cursor is untouched`() = runBlocking {
        dao.applyCatchUp(CHAT, latestEventSequence = 30, messageId = 12, timestampEpochMs = 8_000)

        dao.upsert(feedRow(activity = 5_000, messageId = 10, title = "renamed"))

        assertEquals("renamed", row().title)
        assertEquals(30L, row().latestEventSequence)
        assertEquals(12L, row().lastMessageId)
        assertEquals(8_000L, row().lastActivityEpochMs)
    }

    @Test
    fun `a feed row with no last message keeps the stored one`() = runBlocking {
        dao.upsert(feedRow(activity = 5_000, messageId = null))

        assertEquals(10L, row().lastMessageId)
    }

    private companion object {
        const val CHAT = "aabb"
    }
}
