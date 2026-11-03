package com.flipcash.app.persistence.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.ChatMarkedUnreadEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ChatMarkedUnreadDaoTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var dao: ChatMarkedUnreadDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FlipcashDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.chatMarkedUnreadDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `marking twice keeps one row`() = runTest {
        dao.insertIfAbsent(ChatMarkedUnreadEntity("aa", markedAt = 1_000L))
        dao.insertIfAbsent(ChatMarkedUnreadEntity("aa", markedAt = 9_000L))
        assertEquals(listOf("aa"), dao.observeIds().first())
    }

    @Test
    fun `delete removes only the named chat`() = runTest {
        dao.insertIfAbsent(ChatMarkedUnreadEntity("aa", 1_000L))
        dao.insertIfAbsent(ChatMarkedUnreadEntity("bb", 1_000L))
        dao.delete("bb")
        assertEquals(setOf("aa"), dao.observeIds().first().toSet())
    }

    @Test
    fun `delete by set removes only the named chats`() = runTest {
        listOf("aa", "bb", "cc").forEach { dao.insertIfAbsent(ChatMarkedUnreadEntity(it, 1_000L)) }
        dao.deleteAll(listOf("aa", "cc"))
        assertEquals(setOf("bb"), dao.observeIds().first().toSet())
    }

    @Test
    fun `deleteAll empties the table`() = runTest {
        listOf("aa", "bb").forEach { dao.insertIfAbsent(ChatMarkedUnreadEntity(it, 1_000L)) }
        dao.deleteAll()
        assertEquals(emptyList(), dao.observeIds().first())
    }
}
