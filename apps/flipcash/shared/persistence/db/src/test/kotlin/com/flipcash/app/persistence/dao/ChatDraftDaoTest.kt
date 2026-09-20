package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.ChatDraftEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The `chat_draft` round trip. What a draft is worth is that it comes back exactly as typed, so
 * the cases here are the ones that survive a naive column: whitespace kept verbatim, a row that is
 * text with no reply target, and one chat's draft not disturbing another's.
 */
@RunWith(RobolectricTestRunner::class)
class ChatDraftDaoTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var dao: ChatDraftDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.chatDraftDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun draft(
        chatIdHex: String = CHAT_HEX,
        text: String = "on my way",
        replyTargetJson: String? = null,
        savedAt: Long = 1_000,
    ) = ChatDraftEntity(
        chatIdHex = chatIdHex,
        text = text,
        replyTargetJson = replyTargetJson,
        savedAt = savedAt,
    )

    @Test
    fun `a draft comes back byte for byte`() = runTest {
        val stored = draft(text = "  see you at 5  ", replyTargetJson = REPLY_JSON)
        dao.upsert(stored)

        assertEquals(stored, dao.getByChatId(CHAT_HEX))
    }

    @Test
    fun `a chat with no draft reads as absent rather than empty`() = runTest {
        assertNull(dao.getByChatId(CHAT_HEX))
    }

    @Test
    fun `a second save replaces the first`() = runTest {
        dao.upsert(draft(text = "half a thought", savedAt = 1_000))
        dao.upsert(draft(text = "half a thought, finished", savedAt = 2_000))

        val row = dao.getByChatId(CHAT_HEX)
        assertEquals("half a thought, finished", row?.text)
        assertEquals(2_000, row?.savedAt)
    }

    @Test
    fun `clearing one chat leaves the others`() = runTest {
        dao.upsert(draft(chatIdHex = CHAT_HEX))
        dao.upsert(draft(chatIdHex = OTHER_CHAT_HEX, text = "different chat"))

        dao.deleteByChatId(CHAT_HEX)

        assertNull(dao.getByChatId(CHAT_HEX))
        assertEquals("different chat", dao.getByChatId(OTHER_CHAT_HEX)?.text)
    }

    @Test
    fun `logout empties the table`() = runTest {
        dao.upsert(draft(chatIdHex = CHAT_HEX))
        dao.upsert(draft(chatIdHex = OTHER_CHAT_HEX))

        dao.deleteAll()

        assertNull(dao.getByChatId(CHAT_HEX))
        assertNull(dao.getByChatId(OTHER_CHAT_HEX))
    }

    companion object {
        private const val CHAT_HEX = "a1b2c3d4"
        private const val OTHER_CHAT_HEX = "e5f60718"
        private const val REPLY_JSON =
            """{"messageId":42,"authorName":"Ana","snippet":{"type":"text","body":"are we still on for 5?"}}"""
    }
}
