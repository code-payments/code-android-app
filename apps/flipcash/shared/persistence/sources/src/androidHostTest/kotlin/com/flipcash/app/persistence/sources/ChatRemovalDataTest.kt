package com.flipcash.app.persistence.sources

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.entities.GroupKeyEnvelopeEntity
import com.flipcash.app.persistence.entities.PaymentRequestEntity
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.services.models.chat.ChatId
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Leaving, being removed from, or reconciling away a group drops that chat's key envelopes and
 * payment requests and leaves every other chat's alone. Each path ends in `removeFromChat`; the
 * callers are covered where they live.
 */
@RunWith(RobolectricTestRunner::class)
class ChatRemovalDataTest {

    private lateinit var db: FlipcashDatabase
    private val dataSource = ChatMetadataDataSource(ChatEntityMapper())

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        mockkObject(FlipcashDatabase.Companion)
        every { FlipcashDatabase.getInstance() } returns db
    }

    @After
    fun tearDown() {
        unmockkObject(FlipcashDatabase.Companion)
        db.close()
    }

    private suspend fun seed(chat: String) {
        db.chatMetadataDao().upsert(
            ChatMetadataEntity(
                chatIdHex = chat,
                chatType = "GROUP",
                lastActivityEpochMs = 1,
                lastMessageId = null,
                isMember = true,
            )
        )
        // Two own keys for the same chat: both go.
        for (pk in listOf("01", "02")) {
            db.groupKeyEnvelopeDao().upsert(
                GroupKeyEnvelopeEntity(chat, pk, 1, ByteArray(24), ByteArray(40), null, null)
            )
        }
        db.paymentRequestDao().mergeIfNewer(
            PaymentRequestEntity(
                idHex = "req-$chat",
                chatIdHex = chat,
                version = 1,
                status = PaymentRequestEntity.STATUS_OPEN,
                endsAtEpochMs = null,
                createdAtEpochMs = 1,
                request = byteArrayOf(1),
            )
        )
    }

    private suspend fun assertKept(chat: String) {
        assertNotNull(db.groupKeyEnvelopeDao().find(chat, "01"))
        assertNotNull(db.groupKeyEnvelopeDao().find(chat, "02"))
        assertEquals(1, db.paymentRequestDao().inChat(chat).size)
    }

    @Test
    fun `removing a chat drops its envelopes and requests and keeps another chat's`() = runTest {
        seed("aa")
        seed("bb")

        dataSource.removeFromChat(ChatId(listOf(0xAA.toByte())))

        assertNull(db.groupKeyEnvelopeDao().find("aa", "01"))
        assertNull(db.groupKeyEnvelopeDao().find("aa", "02"))
        assertTrue(db.paymentRequestDao().inChat("aa").isEmpty())
        assertKept("bb")
    }

    @Test
    fun `removing a chat clears its membership but keeps the row`() = runTest {
        seed("aa")
        seed("bb")

        dataSource.removeFromChat("aa")

        assertFalse(db.chatMetadataDao().getById("aa")!!.isMember)
        assertTrue(db.chatMetadataDao().getById("bb")!!.isMember)
    }

    @Test
    fun `removing a chat that holds nothing is a no-op for the others`() = runTest {
        seed("bb")

        dataSource.removeFromChat("aa")

        assertKept("bb")
    }
}
