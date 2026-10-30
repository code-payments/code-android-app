package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.GroupKeyEnvelopeEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class GroupKeyEnvelopeDaoTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var dao: GroupKeyEnvelopeDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.groupKeyEnvelopeDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(chat: String = "aa", pk: String = "01", payload: Byte = 1, wrappedBy: ByteArray? = null) =
        GroupKeyEnvelopeEntity(chat, pk, 1, ByteArray(24) { payload }, ByteArray(40) { payload }, wrappedBy, null)

    @Test
    fun `a row round-trips byte for byte`() = runTest {
        val saved = row(wrappedBy = ByteArray(16) { 4 })
        dao.upsert(saved)

        assertEquals(saved, dao.find("aa", "01"))
    }

    @Test
    fun `upsert replaces the row for the same chat and key`() = runTest {
        dao.upsert(row(payload = 1))
        dao.upsert(row(payload = 2))

        assertEquals(row(payload = 2), dao.find("aa", "01"))
    }

    @Test
    fun `another account's key is a separate row`() = runTest {
        dao.upsert(row(pk = "01", payload = 1))
        dao.upsert(row(pk = "02", payload = 2))
        dao.delete("aa", "01")

        assertNull(dao.find("aa", "01"))
        assertEquals(row(pk = "02", payload = 2), dao.find("aa", "02"))
    }

    @Test
    fun `deleteAll empties the table`() = runTest {
        dao.upsert(row(chat = "aa"))
        dao.upsert(row(chat = "bb"))
        dao.deleteAll()

        assertNull(dao.find("aa", "01"))
        assertNull(dao.find("bb", "01"))
    }
}
