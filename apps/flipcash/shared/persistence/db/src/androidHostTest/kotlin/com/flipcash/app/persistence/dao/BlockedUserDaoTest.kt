package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.BlockedUserEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BlockedUserDaoTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var dao: BlockedUserDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.blockedUserDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `observeIsBlocked is false for a user who is not on the list`() = runTest {
        dao.upsert(listOf(BlockedUserEntity("aa", 1L)))

        dao.observeIsBlocked("bb").test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeIsBlocked is true for a user who is on the list`() = runTest {
        dao.upsert(listOf(BlockedUserEntity("aa", 1L)))

        dao.observeIsBlocked("aa").test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeIsBlocked flips when the user is blocked then unblocked`() = runTest {
        dao.observeIsBlocked("aa").test {
            assertFalse(awaitItem())

            dao.upsert(listOf(BlockedUserEntity("aa", 1L)))
            assertTrue(awaitItem())

            dao.delete("aa")
            assertFalse(awaitItem())
        }
    }
}
