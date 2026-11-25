package com.flipcash.app.persistence.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.ChatArchiveEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ChatArchiveDaoTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var dao: ChatArchiveDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FlipcashDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.chatArchiveDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `put then find returns the write`() = runTest {
        dao.put(ChatArchiveEntity("aa", archivedAt = 1_000L, archived = true))
        assertEquals(ChatArchiveEntity("aa", 1_000L, true), dao.find("aa"))
        assertNull(dao.find("bb"))
    }

    @Test
    fun `a later write on the same chat replaces the one waiting`() = runTest {
        dao.put(ChatArchiveEntity("aa", archivedAt = 1_000L, archived = true))
        dao.put(ChatArchiveEntity("aa", archivedAt = 2_000L, archived = false))
        assertEquals(listOf(ChatArchiveEntity("aa", 2_000L, false)), dao.all())
    }

    @Test
    fun `deleteIfUnchanged keeps a write made while the first was in flight`() = runTest {
        val sent = ChatArchiveEntity("aa", archivedAt = 1_000L, archived = true)
        dao.put(sent)
        dao.put(ChatArchiveEntity("aa", archivedAt = 2_000L, archived = false))

        val deleted = dao.deleteIfUnchanged(sent.chatIdHex, sent.archived, sent.archivedAt)

        assertEquals(0, deleted)
        assertEquals(ChatArchiveEntity("aa", 2_000L, false), dao.find("aa"))
    }

    @Test
    fun `deleteIfUnchanged deletes the write it was given`() = runTest {
        val sent = ChatArchiveEntity("aa", archivedAt = 1_000L, archived = true)
        dao.put(sent)
        assertEquals(1, dao.deleteIfUnchanged(sent.chatIdHex, sent.archived, sent.archivedAt))
        assertNull(dao.find("aa"))
    }

    @Test
    fun `observeAll emits every waiting write`() = runTest {
        dao.put(ChatArchiveEntity("aa", archivedAt = 1_000L))
        dao.put(ChatArchiveEntity("bb", archivedAt = 2_000L, archived = false))
        assertEquals(setOf("aa", "bb"), dao.observeAll().first().map { it.chatIdHex }.toSet())
    }

    @Test
    fun `delete and deleteAll`() = runTest {
        dao.put(ChatArchiveEntity("aa", archivedAt = 1_000L))
        dao.put(ChatArchiveEntity("bb", archivedAt = 2_000L))
        dao.delete("aa")
        assertEquals(listOf("bb"), dao.all().map { it.chatIdHex })
        dao.deleteAll()
        assertTrue(dao.all().isEmpty())
    }
}
