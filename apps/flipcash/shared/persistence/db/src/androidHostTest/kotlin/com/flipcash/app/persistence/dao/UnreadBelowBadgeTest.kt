package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.converters.MessageContentSerialized
import com.flipcash.app.persistence.converters.MessagePointerSerialized
import com.flipcash.app.persistence.entities.ChatMemberEntity
import com.flipcash.app.persistence.entities.ChatMessageEntity
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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
        val dividerCount = messages.countInboundAfter(CHAT_HEX, SELF_HEX, 2)

        badge().test {
            assertEquals(dividerCount, awaitItem())
            assertEquals(2, dividerCount)
            expectNoEvents()
        }
    }

    @Test
    fun `advancing the read pointer emits 3 then 1 and ignores a late lower advance`() = runTest {
        messages.upsert(listOf(message(OTHER_HEX, 1), message(OTHER_HEX, 2), message(OTHER_HEX, 3), message(OTHER_HEX, 4)))
        open(readThrough = 1)

        badge().test {
            assertEquals(3, awaitItem())

            members.advancePointer(CHAT_HEX, SELF_HEX, pointer(3))
            assertEquals(1, awaitItem())

            // The pointer keeps its max: a late, lower advance doesn't bring the count back.
            members.advancePointer(CHAT_HEX, SELF_HEX, pointer(2))
            expectNoEvents()
        }
    }

    @Test
    fun `an inbound arrival below emits n then n plus 1`() = runTest {
        messages.upsert(listOf(message(OTHER_HEX, 1), message(OTHER_HEX, 2)))
        open(readThrough = 2)

        badge().test {
            assertEquals(0, awaitItem())

            messages.upsert(listOf(message(OTHER_HEX, 3)))
            assertEquals(1, awaitItem())

            messages.upsert(listOf(message(OTHER_HEX, 4)))
            assertEquals(2, awaitItem())
        }
    }

    @Test
    fun `own messages are never counted and the pointer reaching an own send clears the badge`() = runTest {
        messages.upsert(listOf(message(OTHER_HEX, 1), message(OTHER_HEX, 2), message(OTHER_HEX, 3)))
        open(readThrough = 0)

        badge().test {
            assertEquals(3, awaitItem())

            // An own row changes nothing.
            messages.upsert(listOf(message(SELF_HEX, 4)))
            expectNoEvents()

            // MessagingDelegate.send advances the pointer to the id the server gives the sent
            // message on success, which is above every inbound id; that is what clears the badge.
            members.advancePointer(CHAT_HEX, SELF_HEX, pointer(4))
            assertEquals(0, awaitItem())
        }
    }

    @Test
    fun `no self row emits 0`() = runTest {
        messages.upsert(listOf(message(OTHER_HEX, 1)))

        badge().test {
            assertEquals(0, awaitItem())
            expectNoEvents()
        }
    }

    private companion object {
        const val CHAT_HEX = "c1"
        const val SELF_HEX = "aa"
        const val OTHER_HEX = "bb"
    }
}
