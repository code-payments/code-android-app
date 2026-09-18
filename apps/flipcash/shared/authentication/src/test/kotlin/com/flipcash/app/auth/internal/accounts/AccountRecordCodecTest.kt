package com.flipcash.app.auth.internal.accounts

import com.getcode.utils.encodeBase64
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AccountRecordCodecTest {

    private fun entropy(seed: Int): String = Random(seed).nextBytes(16).encodeBase64()

    private fun record(seed: Int, lastSeen: Long = 1_000L, deleted: Long? = null) =
        AccountRecord(
            entropy = entropy(seed),
            creationDate = 500L,
            lastSeen = lastSeen,
            deletionDate = deleted,
        )

    @Test
    fun `round trips a single record`() {
        val records = listOf(record(1))
        assertEquals(records, AccountRecordCodec.decode(AccountRecordCodec.encode(records)))
    }

    @Test
    fun `round trips a soft-deleted record`() {
        val records = listOf(record(1, deleted = 9_999L))
        assertEquals(records, AccountRecordCodec.decode(AccountRecordCodec.encode(records)))
    }

    @Test
    fun `round trips a full list and preserves order`() {
        val records = (1..AccountRecordCodec.MAX_ACCOUNTS).map { record(it, lastSeen = it.toLong()) }
        assertEquals(records, AccountRecordCodec.decode(AccountRecordCodec.encode(records)))
    }

    @Test
    fun `a full list fits well inside the block store entry`() {
        val records = (1..AccountRecordCodec.MAX_ACCOUNTS).map { record(it) }
        assertTrue(AccountRecordCodec.encode(records).size <= 2048)
    }

    @Test
    fun `encoding over the cap evicts the least recently seen`() {
        val records = (1..AccountRecordCodec.MAX_ACCOUNTS + 3).map { record(it, lastSeen = it.toLong()) }
        val decoded = AccountRecordCodec.decode(AccountRecordCodec.encode(records))

        assertEquals(AccountRecordCodec.MAX_ACCOUNTS, decoded.size)
        // lastSeen 1, 2 and 3 are the oldest, so they are the ones dropped.
        assertTrue(decoded.none { it.lastSeen <= 3L })
    }

    @Test
    fun `decodes empty bytes as an empty list`() {
        assertEquals(emptyList<AccountRecord>(), AccountRecordCodec.decode(ByteArray(0)))
    }

    @Test
    fun `decodes an unknown version as an empty list`() {
        val bytes = AccountRecordCodec.encode(listOf(record(1)))
        bytes[0] = 99
        assertEquals(emptyList<AccountRecord>(), AccountRecordCodec.decode(bytes))
    }

    @Test
    fun `decodes truncated bytes as an empty list`() {
        val bytes = AccountRecordCodec.encode(listOf(record(1), record(2)))
        assertEquals(emptyList<AccountRecord>(), AccountRecordCodec.decode(bytes.copyOf(bytes.size - 5)))
    }

    /**
     * Throwing here would cancel the write that noticed the bad record, and the flow collecting
     * it, so the record goes and the rest of the list survives.
     */
    @Test
    fun `encoding drops a wrong-length entropy and keeps the rest`() {
        val bad = AccountRecord(
            entropy = "AA", // decodes to one byte, not sixteen
            creationDate = 500L,
            lastSeen = 1_000L,
        )
        val good = record(1)

        val decoded = AccountRecordCodec.decode(AccountRecordCodec.encode(listOf(bad, good)))

        assertEquals(listOf(good), decoded)
    }
}
