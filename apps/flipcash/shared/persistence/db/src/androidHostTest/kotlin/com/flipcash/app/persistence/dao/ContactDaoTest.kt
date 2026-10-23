package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.ContactMappingEntity
import com.flipcash.app.persistence.entities.ContactSyncStateEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ContactDaoTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var dao: ContactDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.contactDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // -- helpers --

    private fun makeMapping(
        e164: String = "+1555${System.nanoTime() % 10_000_000}",
        contactId: Long = 1L,
        displayName: String = "Test User",
        photoUri: String? = null,
        isOnFlipcash: Boolean = false,
    ) = ContactMappingEntity(
        e164 = e164,
        androidContactId = contactId,
        displayName = displayName,
        photoUri = photoUri,
        isOnFlipcash = isOnFlipcash,
    )

    private fun makeSyncState(
        checksumBytes: ByteArray = ByteArray(32) { it.toByte() },
        timestamp: Long = System.currentTimeMillis(),
        needsFullUpload: Boolean = false,
    ) = ContactSyncStateEntity(
        checksumBytes = checksumBytes,
        lastSyncTimestamp = timestamp,
        needsFullUpload = needsFullUpload,
    )

    // -- Sync state tests --

    @Test
    fun `getSyncState returns null when empty`() = runTest {
        assertNull(dao.getSyncState())
    }

    @Test
    fun `upsertSyncState and getSyncState roundtrip`() = runTest {
        val state = makeSyncState(timestamp = 12345L)
        dao.upsertSyncState(state)

        val result = dao.getSyncState()
        assertNotNull(result)
        assertEquals(12345L, result.lastSyncTimestamp)
        assertTrue(result.checksumBytes.contentEquals(state.checksumBytes))
    }

    @Test
    fun `upsertSyncState replaces existing`() = runTest {
        dao.upsertSyncState(makeSyncState(timestamp = 100L))
        dao.upsertSyncState(makeSyncState(timestamp = 200L))

        val result = dao.getSyncState()
        assertNotNull(result)
        assertEquals(200L, result.lastSyncTimestamp)
    }

    @Test
    fun `clearSyncState removes sync state`() = runTest {
        dao.upsertSyncState(makeSyncState())
        assertNotNull(dao.getSyncState())

        dao.clearSyncState()
        assertNull(dao.getSyncState())
    }

    // -- Contact mapping tests --

    @Test
    fun `upsertMappings and getAllMappings roundtrip`() = runTest {
        val m1 = makeMapping(e164 = "+15551111111", displayName = "Alice")
        val m2 = makeMapping(e164 = "+15552222222", displayName = "Bob")
        dao.upsertMappings(listOf(m1, m2))

        val all = dao.getAllMappings()
        assertEquals(2, all.size)
        assertTrue(all.any { it.displayName == "Alice" })
        assertTrue(all.any { it.displayName == "Bob" })
    }

    @Test
    fun `upsertMappings replaces on conflict`() = runTest {
        dao.upsertMappings(listOf(makeMapping(e164 = "+15551111111", displayName = "Old")))
        dao.upsertMappings(listOf(makeMapping(e164 = "+15551111111", displayName = "New")))

        val all = dao.getAllMappings()
        assertEquals(1, all.size)
        assertEquals("New", all.first().displayName)
    }

    @Test
    fun `deleteMappings removes specified entries`() = runTest {
        dao.upsertMappings(listOf(
            makeMapping(e164 = "+15551111111"),
            makeMapping(e164 = "+15552222222"),
            makeMapping(e164 = "+15553333333"),
        ))
        assertEquals(3, dao.getAllMappings().size)

        dao.deleteMappings(listOf("+15551111111", "+15553333333"))
        val remaining = dao.getAllMappings()
        assertEquals(1, remaining.size)
        assertEquals("+15552222222", remaining.first().e164)
    }

    // -- Flipcash status tests --

    @Test
    fun `markAsFlipcash updates specified entries`() = runTest {
        dao.upsertMappings(listOf(
            makeMapping(e164 = "+15551111111"),
            makeMapping(e164 = "+15552222222"),
            makeMapping(e164 = "+15553333333"),
        ))

        dao.markAsFlipcash(listOf("+15551111111", "+15553333333"))

        val all = dao.getAllMappings()
        assertTrue(all.first { it.e164 == "+15551111111" }.isOnFlipcash)
        assertTrue(!all.first { it.e164 == "+15552222222" }.isOnFlipcash)
        assertTrue(all.first { it.e164 == "+15553333333" }.isOnFlipcash)
    }

    @Test
    fun `clearFlipcashStatus resets all to false`() = runTest {
        dao.upsertMappings(listOf(
            makeMapping(e164 = "+15551111111", isOnFlipcash = true),
            makeMapping(e164 = "+15552222222", isOnFlipcash = true),
        ))

        dao.clearFlipcashStatus()

        val all = dao.getAllMappings()
        assertTrue(all.none { it.isOnFlipcash })
    }

    // -- Flow observation tests --

    @Test
    fun `observeFlipcashContacts emits only Flipcash contacts`() = runTest {
        dao.upsertMappings(listOf(
            makeMapping(e164 = "+15551111111", isOnFlipcash = true),
            makeMapping(e164 = "+15552222222", isOnFlipcash = false),
        ))

        dao.observeFlipcashContacts().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("+15551111111", result.first().e164)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeNonFlipcashContacts emits only non-Flipcash contacts`() = runTest {
        dao.upsertMappings(listOf(
            makeMapping(e164 = "+15551111111", isOnFlipcash = true),
            makeMapping(e164 = "+15552222222", isOnFlipcash = false),
        ))

        dao.observeNonFlipcashContacts().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("+15552222222", result.first().e164)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeAllMappings emits on changes`() = runTest {
        dao.observeAllMappings().test {
            assertEquals(emptyList(), awaitItem())

            dao.upsertMappings(listOf(makeMapping(e164 = "+15551111111")))
            assertEquals(1, awaitItem().size)

            dao.upsertMappings(listOf(makeMapping(e164 = "+15552222222")))
            assertEquals(2, awaitItem().size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // -- clearAll tests --

    @Test
    fun `clearAll removes sync state and all mappings`() = runTest {
        dao.upsertSyncState(makeSyncState())
        dao.upsertMappings(listOf(
            makeMapping(e164 = "+15551111111", isOnFlipcash = true),
        ))

        dao.clearAll()

        assertNull(dao.getSyncState())
        assertTrue(dao.getAllMappings().isEmpty())
    }
}
