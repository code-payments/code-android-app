package com.flipcash.app.persistence.sources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.converters.MessageContentSerialized
import com.flipcash.app.persistence.converters.MessagePointerSerialized
import com.flipcash.app.persistence.entities.ChatMemberEntity
import com.flipcash.app.persistence.entities.ChatMessageEntity
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.services.models.chat.ChatId
import com.getcode.utils.hexEncodedString
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * The per-user database is created at login, so the jump-to-bottom badge's streams can be
 * subscribed before it exists. They must wait for it, not complete empty for good.
 */
@RunWith(RobolectricTestRunner::class)
class UnreadStreamReadinessTest {

    private val mapper = ChatEntityMapper()
    private val members = ChatMemberDataSource(mapper)
    private val messages = ChatMessageDataSource(mapper, mockk(relaxed = true), mockk(relaxed = true))

    private val chatId = ChatId(listOf(0xAB.toByte()))
    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)

    @After
    fun tearDown() {
        FlipcashDatabase.closeDb()
    }

    private fun openDatabase() {
        FlipcashDatabase.init(ApplicationProvider.getApplicationContext<Context>(), "entropy-a")
    }

    @Test
    fun `the read pointer is null until the database exists and then follows it`() = runTest {
        FlipcashDatabase.closeDb()

        members.observeSelfReadPointer(chatId, selfId).test {
            assertEquals(null, awaitItem())

            openDatabase()
            val db = FlipcashDatabase.requireInstance()
            db.chatMemberDao().upsert(
                ChatMemberEntity(
                    chatIdHex = mapper.chatIdHex(chatId),
                    userIdHex = selfId.hexEncodedString(),
                    pointersJson = listOf(
                        MessagePointerSerialized(type = "READ", userIdHex = selfId.hexEncodedString(), value = 2),
                    ),
                ),
            )
            assertEquals(2L, awaitItem())
        }
    }

    @Test
    fun `the inbound count is 0 until the database exists and then counts`() = runTest {
        FlipcashDatabase.closeDb()

        messages.observeInboundCountAfter(chatId, selfId, 1).test {
            assertEquals(0, awaitItem())

            openDatabase()
            FlipcashDatabase.requireInstance().chatMessageDao().upsert(
                listOf(2L, 3L).map { id ->
                    ChatMessageEntity(
                        chatIdHex = mapper.chatIdHex(chatId),
                        messageId = id,
                        senderIdHex = otherId.hexEncodedString(),
                        contentJson = listOf(MessageContentSerialized.Text("m$id")),
                        timestampEpochMs = id * 1_000,
                        unreadSeq = id,
                    )
                },
            )
            assertEquals(2, awaitItem())
        }
    }
}
