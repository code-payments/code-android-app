package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.PaymentRequestEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The `paymentrequest.v1` merge rule as the database enforces it: the greater version replaces the
 * row whole, and at an equal version only EXPIRED over OPEN does.
 */
@RunWith(RobolectricTestRunner::class)
class PaymentRequestDaoTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var dao: PaymentRequestDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.paymentRequestDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        version: Long,
        status: String = PaymentRequestEntity.STATUS_OPEN,
        payload: String = "v$version-$status",
        id: String = ID,
        chat: String = CHAT,
    ) = PaymentRequestEntity(
        idHex = id,
        chatIdHex = chat,
        version = version,
        status = status,
        endsAtEpochMs = 9_000,
        createdAtEpochMs = 1_000,
        request = payload.toByteArray(),
    )

    private suspend fun stored(id: String = ID) = dao.find(id)?.let { it.version to String(it.request) }

    @Test
    fun `a request with no row is written`() = runTest {
        dao.mergeIfNewer(entity(version = 3))

        assertEquals(entity(version = 3), dao.find(ID))
    }

    @Test
    fun `an older version keeps the stored row`() = runTest {
        dao.mergeIfNewer(entity(version = 5))
        dao.mergeIfNewer(entity(version = 4, payload = "stale"))

        assertEquals(5L to "v5-OPEN", stored())
    }

    @Test
    fun `a newer version replaces the whole row`() = runTest {
        dao.mergeIfNewer(entity(version = 5, chat = "aa"))
        dao.mergeIfNewer(entity(version = 6, status = PaymentRequestEntity.STATUS_COMPLETED, chat = "bb"))

        assertEquals(entity(version = 6, status = PaymentRequestEntity.STATUS_COMPLETED, chat = "bb"), dao.find(ID))
    }

    @Test
    fun `an equal version EXPIRED replaces OPEN`() = runTest {
        dao.mergeIfNewer(entity(version = 5))
        dao.mergeIfNewer(entity(version = 5, status = PaymentRequestEntity.STATUS_EXPIRED))

        assertEquals(5L to "v5-EXPIRED", stored())
        assertEquals(PaymentRequestEntity.STATUS_EXPIRED, dao.find(ID)?.status)
    }

    @Test
    fun `an equal version OPEN does not replace EXPIRED`() = runTest {
        dao.mergeIfNewer(entity(version = 5, status = PaymentRequestEntity.STATUS_EXPIRED))
        dao.mergeIfNewer(entity(version = 5, status = PaymentRequestEntity.STATUS_OPEN))

        assertEquals(PaymentRequestEntity.STATUS_EXPIRED, dao.find(ID)?.status)
    }

    @Test
    fun `an equal version with any other status leaves the row alone`() = runTest {
        dao.mergeIfNewer(entity(version = 5))
        dao.mergeIfNewer(entity(version = 5, status = PaymentRequestEntity.STATUS_CLOSED, payload = "other"))

        assertEquals(5L to "v5-OPEN", stored())
    }

    @Test
    fun `an older version never replaces even an EXPIRED over OPEN`() = runTest {
        dao.mergeIfNewer(entity(version = 5))
        dao.mergeIfNewer(entity(version = 4, status = PaymentRequestEntity.STATUS_EXPIRED))

        assertEquals(5L to "v5-OPEN", stored())
    }

    @Test
    fun `requests are listed by chat`() = runTest {
        dao.mergeIfNewer(entity(version = 1, id = "01", chat = "aa"))
        dao.mergeIfNewer(entity(version = 1, id = "02", chat = "aa"))
        dao.mergeIfNewer(entity(version = 1, id = "03", chat = "bb"))

        assertEquals(setOf("01", "02"), dao.inChat("aa").map { it.idHex }.toSet())
    }

    @Test
    fun `deleteAll clears every request`() = runTest {
        dao.mergeIfNewer(entity(version = 1))
        dao.deleteAll()

        assertNull(dao.find(ID))
    }

    private companion object {
        const val ID = "0a0b"
        const val CHAT = "aa"
    }
}
