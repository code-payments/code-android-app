package com.flipcash.app.persistence.sources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.converters.MessageContentSerialized
import com.flipcash.app.persistence.entities.ChatMessageEntity
import com.flipcash.app.persistence.init
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.services.models.chat.ChatId
import com.getcode.utils.hexEncodedString
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Mark as Read from the chat list moves READ to [ChatMessageDataSource.getLatestMessageId]. The
 * list's unread count compares the pointer by message id against the newest visible message, so
 * the anchor has to be the highest stored id. Picking the newest row by timestamp landed short
 * whenever timestamps and ids disagreed, and the count dropped instead of clearing.
 */
@RunWith(RobolectricTestRunner::class)
class MarkReadAnchorTest {

    private val mapper = ChatEntityMapper()
    private val messages = ChatMessageDataSource(mapper, mockk(relaxed = true), mockk(relaxed = true))

    private val chatId = ChatId(listOf(0xAB.toByte()))
    private val otherId = listOf<Byte>(4, 5, 6)

    @Before
    fun setUp() {
        FlipcashDatabase.init(ApplicationProvider.getApplicationContext<Context>(), "entropy-a")
    }

    @After
    fun tearDown() {
        FlipcashDatabase.closeDb()
    }

    private fun inbound(messageId: Long, timestampEpochMs: Long) = ChatMessageEntity(
        chatIdHex = mapper.chatIdHex(chatId),
        messageId = messageId,
        senderIdHex = otherId.hexEncodedString(),
        contentJson = listOf(MessageContentSerialized.Text("m$messageId")),
        timestampEpochMs = timestampEpochMs,
        unreadSeq = messageId,
    )

    private suspend fun store(vararg rows: ChatMessageEntity) {
        FlipcashDatabase.requireInstance().chatMessageDao().upsert(rows.toList())
    }

    @Test
    fun `the anchor is the newest message when it shares a millisecond with an older one`() = runTest {
        // The feed writes the chat's newest message first; the history load fills in older ones
        // afterwards, so the older row is the later insert.
        store(inbound(messageId = 5, timestampEpochMs = 4_000))
        store(
            inbound(messageId = 1, timestampEpochMs = 1_000),
            inbound(messageId = 2, timestampEpochMs = 2_000),
            inbound(messageId = 3, timestampEpochMs = 3_000),
            inbound(messageId = 4, timestampEpochMs = 4_000),
        )

        assertEquals(5L, messages.getLatestVisible(mapper.chatIdHex(chatId))?.messageId)
        assertEquals(5L, messages.getLatestMessageId(chatId))
    }

    @Test
    fun `the anchor is the highest id when an older message carries a later timestamp`() = runTest {
        store(
            inbound(messageId = 1, timestampEpochMs = 1_000),
            inbound(messageId = 2, timestampEpochMs = 9_000).copy(isDeleted = true),
            inbound(messageId = 3, timestampEpochMs = 3_000),
        )

        assertEquals(3L, messages.getLatestVisible(mapper.chatIdHex(chatId))?.messageId)
        assertEquals(3L, messages.getLatestMessageId(chatId))
    }

    @Test
    fun `an unsent placeholder is not an anchor`() = runTest {
        store(
            inbound(messageId = 1, timestampEpochMs = 1_000),
            inbound(messageId = 2, timestampEpochMs = 2_000),
            // A pending send is stamped with the device clock, which can run ahead of the server's.
            inbound(messageId = -3_000, timestampEpochMs = 3_000).copy(senderIdHex = "010203"),
        )

        assertEquals(2L, messages.getLatestMessageId(chatId))
    }

    @Test
    fun `a newest tombstone is still the anchor`() = runTest {
        store(
            inbound(messageId = 1, timestampEpochMs = 1_000),
            inbound(messageId = 2, timestampEpochMs = 2_000).copy(isDeleted = true),
        )

        assertEquals(2L, messages.getLatestMessageId(chatId))
    }
}
