package com.flipcash.app.auth.internal.accounts

import com.getcode.utils.encodeBase64
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// `encodeBase64` is a KMP actual over `android.util.Base64`. The module sets
// `unitTests.isReturnDefaultValues = true`, so on the plain JVM runner it returns null rather
// than throwing. Robolectric supplies the real implementation, as it does in
// `libs/encryption/utils`' own `Base64ExtensionsTest`.
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BlockStoreAccountStoreTest {

    /**
     * Reads and writes fail independently, because Block Store's failure modes are not symmetric:
     * a read can fail on its own, and that is the case that can cost the list its contents.
     */
    private class FakeBlockStoreBytes(var stored: ByteArray? = null) : BlockStoreBytes {
        var canRead = true
        var canWrite = true
        override suspend fun read(): ByteArray? =
            if (canRead) stored ?: ByteArray(0) else null
        override suspend fun write(bytes: ByteArray): Boolean {
            if (!canWrite) return false
            stored = bytes
            return true
        }
        override suspend fun delete() { stored = null }
    }

    private fun entropy(seed: Int): String = Random(seed).nextBytes(16).encodeBase64()

    private fun store(
        bytes: FakeBlockStoreBytes = FakeBlockStoreBytes(),
        clock: () -> Long = { 1_000L },
    ) = BlockStoreAccountStore(bytes, clock)

    @Test
    fun `starts empty`() = runTest {
        assertEquals(emptyList(), store().all())
    }

    @Test
    fun `upsert inserts an account`() = runTest {
        val store = store()
        store.upsert(entropy(1))

        val all = store.all()
        assertEquals(1, all.size)
        assertEquals(entropy(1), all.first().entropy)
        assertEquals(1_000L, all.first().creationDate)
        assertEquals(1_000L, all.first().lastSeen)
        assertNull(all.first().deletionDate)
    }

    @Test
    fun `upsert of a known account bumps lastSeen and keeps creationDate`() = runTest {
        var now = 1_000L
        val bytes = FakeBlockStoreBytes()
        store(bytes) { now }.upsert(entropy(1))

        now = 5_000L
        val store = store(bytes) { now }
        store.upsert(entropy(1))

        val all = store.all()
        assertEquals(1, all.size)
        assertEquals(1_000L, all.first().creationDate)
        assertEquals(5_000L, all.first().lastSeen)
    }

    @Test
    fun `setDeleted hides the account from all but keeps the record`() = runTest {
        val store = store()
        store.upsert(entropy(1))
        store.setDeleted(entropy(1), deleted = true)

        assertTrue(store.all().isEmpty())
        assertEquals(1, store.allIncludingDeleted().size)
    }

    @Test
    fun `upsert undeletes and preserves the original creationDate`() = runTest {
        var now = 1_000L
        val bytes = FakeBlockStoreBytes()
        store(bytes) { now }.let {
            it.upsert(entropy(1))
            it.setDeleted(entropy(1), deleted = true)
        }

        now = 9_000L
        val store = store(bytes) { now }
        store.upsert(entropy(1))

        val all = store.all()
        assertEquals(1, all.size)
        assertEquals(1_000L, all.first().creationDate)
        assertNull(all.first().deletionDate)
    }

    @Test
    fun `all returns newest created first`() = runTest {
        var now = 1_000L
        val bytes = FakeBlockStoreBytes()
        store(bytes) { now }.upsert(entropy(1))
        now = 2_000L
        store(bytes) { now }.upsert(entropy(2))

        assertEquals(listOf(entropy(2), entropy(1)), store(bytes).all().map { it.entropy })
    }

    @Test
    fun `clear empties the store`() = runTest {
        val bytes = FakeBlockStoreBytes()
        val store = store(bytes)
        store.upsert(entropy(1))
        store.clear()

        assertTrue(store.all().isEmpty())
        assertNull(bytes.stored)
    }

    @Test
    fun `degrades to empty when the store is unavailable`() = runTest {
        val bytes = FakeBlockStoreBytes().apply {
            canRead = false
            canWrite = false
        }
        val store = store(bytes)

        store.upsert(entropy(1))

        assertEquals(emptyList(), store.all())
    }

    /**
     * The failure that costs accounts: a read fails while the write still lands. Rewriting the
     * entry from an unread list would replace every account with the one this mutation produces.
     */
    @Test
    fun `leaves the stored list alone when the read fails and the write would succeed`() = runTest {
        var now = 1_000L
        val bytes = FakeBlockStoreBytes()
        store(bytes) { now }.upsert(entropy(1))
        now = 2_000L
        store(bytes) { now }.upsert(entropy(2))
        val before = bytes.stored

        bytes.canRead = false
        store(bytes).upsert(entropy(3))

        bytes.canRead = true
        assertContentEquals(before, bytes.stored)
        assertEquals(listOf(entropy(2), entropy(1)), store(bytes).all().map { it.entropy })
    }
}
