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
    fun `insert then find returns the id`() = runTest {
        dao.insertIfAbsent(ChatArchiveEntity("aa", archivedAt = 1_000L))
        assertEquals("aa", dao.find("aa"))
    }

    @Test
    fun `find is null for a chat that was never archived`() = runTest {
        assertNull(dao.find("zz"))
    }

    @Test
    fun `archiving twice keeps the first timestamp`() = runTest {
        dao.insertIfAbsent(ChatArchiveEntity("aa", archivedAt = 1_000L))
        dao.insertIfAbsent(ChatArchiveEntity("aa", archivedAt = 9_000L))
        assertEquals(1_000L, dao.archivedAt("aa"))
    }

    @Test
    fun `delete removes only the named chat`() = runTest {
        dao.insertIfAbsent(ChatArchiveEntity("aa", 1_000L))
        dao.insertIfAbsent(ChatArchiveEntity("bb", 1_000L))
        dao.delete("bb")
        assertEquals("aa", dao.find("aa"))
        assertNull(dao.find("bb"))
    }

    @Test
    fun `deleteAll empties the table`() = runTest {
        dao.insertIfAbsent(ChatArchiveEntity("aa", 1_000L))
        dao.insertIfAbsent(ChatArchiveEntity("bb", 1_000L))
        dao.deleteAll()
        assertEquals(emptyList(), dao.observeIds().first())
    }

    @Test
    fun `observeIds emits the current set and re-emits on change`() = runTest {
        assertEquals(emptyList(), dao.observeIds().first())
        dao.insertIfAbsent(ChatArchiveEntity("aa", 1_000L))
        assertEquals(listOf("aa"), dao.observeIds().first())
    }
}
