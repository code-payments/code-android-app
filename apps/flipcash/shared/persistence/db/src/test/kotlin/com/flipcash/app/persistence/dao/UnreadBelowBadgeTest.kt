package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.converters.MessageContentSerialized
import com.flipcash.app.persistence.converters.MessagePointerSerialized
import com.flipcash.app.persistence.entities.ChatMemberEntity
import com.flipcash.app.persistence.entities.ChatMessageEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * The jump-to-bottom badge: the viewer's stored READ pointer feeding the divider's own inbound
 * count, live. The pointer is the one the read reporter advances, so the badge can't disagree
 * with it, and the count is the divider's query, so it opens at the divider's number.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UnreadBelowBadgeTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var messages: ChatMessageDao
    private lateinit var members: ChatMemberDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        messages = db.chatMessageDao()
        members = db.chatMemberDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** The same composition MessagingDelegate.observeUnreadBelow runs over the data sources. */
    private fun badge(): Flow<Int> =
        members.observeMember(CHAT_HEX, SELF_HEX)
            .map { self ->
                self?.let { it.pointersJson.orEmpty().firstOrNull { p -> p.type == "READ" }?.value ?: 0L }
            }
            .distinctUntilChanged()
            .flatMapLatest { readThrough ->
                if (readThrough == null) flowOf(0)
                else messages.observeInboundCountAfter(CHAT_HEX, SELF_HEX, readThrough)
            }
            .distinctUntilChanged()

    private fun message(sender: String, id: Long) = ChatMessageEntity(
        chatIdHex = CHAT_HEX,
        messageId = id,
        senderIdHex = sender,
        contentJson = listOf(MessageContentSerialized.Text("m$id")),
        timestampEpochMs = id * 1_000,
        unreadSeq = id,
    )

    private fun pointer(value: Long) = MessagePointerSerialized(type = "READ", userIdHex = SELF_HEX, value = value)

    private suspend fun open(readThrough: Long) {
        members.upsert(ChatMemberEntity(chatIdHex = CHAT_HEX, userIdHex = SELF_HEX, pointersJson = listOf(pointer(readThrough))))
    }

    @Test
    fun `opens at the unread divider's count`() = runTest {
        messages.upsert(
            listOf(message(OTHER_HEX, 1), message(OTHER_HEX, 2), message(SELF_HEX, 3), message(OTHER_HEX, 4), message(OTHER_HEX, 5)),
        )
        open(readThrough = 2)

        assertEquals(messages.countInboundAfter(CHAT_HEX, SELF_HEX, 2), badge().first())
        assertEquals(2, badge().first())
    }

    @Test
    fun `advancing the read pointer decrements`() = runTest {
        messages.upsert(listOf(message(OTHER_HEX, 1), message(OTHER_HEX, 2), message(OTHER_HEX, 3), message(OTHER_HEX, 4)))
        open(readThrough = 1)
        assertEquals(3, badge().first())

        members.advancePointer(CHAT_HEX, SELF_HEX, pointer(3))
        assertEquals(1, badge().first())

        // The pointer keeps its max: a late, lower advance doesn't bring the count back.
        members.advancePointer(CHAT_HEX, SELF_HEX, pointer(2))
        assertEquals(1, badge().first())
    }

    @Test
    fun `an inbound arrival below increments`() = runTest {
        messages.upsert(listOf(message(OTHER_HEX, 1), message(OTHER_HEX, 2)))
        open(readThrough = 2)
        assertEquals(0, badge().first())

        messages.upsert(listOf(message(OTHER_HEX, 3)))
        assertEquals(1, badge().first())
    }

    @Test
    fun `own send clears once the pointer reaches it, and own messages are never counted`() = runTest {
        messages.upsert(listOf(message(OTHER_HEX, 1), message(OTHER_HEX, 2), message(OTHER_HEX, 3)))
        open(readThrough = 0)
        assertEquals(3, badge().first())

        // MessagingDelegate.send advances the pointer to the id the server gives the sent message.
        messages.upsert(listOf(message(SELF_HEX, 4)))
        assertEquals(3, badge().first())
        members.advancePointer(CHAT_HEX, SELF_HEX, pointer(4))
        assertEquals(0, badge().first())
    }

    @Test
    fun `no self row counts nothing`() = runTest {
        messages.upsert(listOf(message(OTHER_HEX, 1)))

        assertEquals(0, badge().first())
    }

    private companion object {
        const val CHAT_HEX = "c1"
        const val SELF_HEX = "aa"
        const val OTHER_HEX = "bb"
    }
}
