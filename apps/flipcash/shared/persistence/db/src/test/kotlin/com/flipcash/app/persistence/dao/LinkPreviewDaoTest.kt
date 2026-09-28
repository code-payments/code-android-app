package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.LinkPreviewEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class LinkPreviewDaoTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var dao: LinkPreviewDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.linkPreviewDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `one link is one row, and a newer answer replaces the older`() = runTest {
        dao.upsert(LinkPreviewEntity(key = "group:ab", json = "{\"memberCount\":1}", updatedAt = 1))
        dao.upsert(LinkPreviewEntity(key = "group:ab", json = "{\"memberCount\":2}", updatedAt = 2))
        dao.upsert(LinkPreviewEntity(key = "user:name:satoshi", json = "{}", updatedAt = 3))

        val rows = dao.getAll().associateBy { it.key }
        assertEquals(2, rows.size)
        assertEquals("{\"memberCount\":2}", rows.getValue("group:ab").json)
    }

    @Test
    fun `a deleted link is gone`() = runTest {
        dao.upsert(LinkPreviewEntity(key = "group:ab", json = "{}", updatedAt = 1))
        dao.delete("group:ab")

        assertEquals(emptyList(), dao.getAll())
    }
}
