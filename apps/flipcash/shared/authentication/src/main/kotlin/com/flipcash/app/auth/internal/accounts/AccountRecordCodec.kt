package com.flipcash.app.auth.internal.accounts

import com.getcode.utils.TraceType
import com.getcode.utils.decodeBase64
import com.getcode.utils.encodeBase64
import com.getcode.utils.trace
import java.nio.ByteBuffer

/**
 * Packs the account list into the single Block Store entry.
 *
 * Layout: `[version:1][count:2][record × count]`, each record a fixed 40 bytes of
 * `[entropy:16][creationDate:8][lastSeen:8][deletionDate:8]`, with `0` standing in for a null
 * deletion date. That is 2003 bytes at the cap, roughly half of Block Store's 4KB per-entry
 * limit, which leaves room for a format change.
 *
 * Every decode failure returns an empty list rather than throwing: a corrupt entry must not
 * brick login, and the migration repopulates from the local DataStore.
 */
internal object AccountRecordCodec {

    private const val TAG = "BlockStore"

    const val VERSION: Byte = 1
    const val MAX_ACCOUNTS = 50

    private const val ENTROPY_BYTES = 16
    private const val RECORD_BYTES = ENTROPY_BYTES + 8 + 8 + 8
    private const val HEADER_BYTES = 1 + 2

    /**
     * A record whose entropy is not a 16-byte seed is dropped rather than thrown on. The only way
     * one reaches here is the legacy DataStore the migration reads, and no such record names a
     * usable account — whereas throwing would take down the write that noticed it, and every
     * later write on the same list with it.
     */
    fun encode(records: List<AccountRecord>): ByteArray {
        val encodable = cap(records).mapNotNull { record ->
            val entropy = runCatching { record.entropy.decodeBase64() }.getOrNull()
            if (entropy?.size != ENTROPY_BYTES) {
                trace(
                    tag = TAG,
                    message = "Dropping a stored account whose entropy is not $ENTROPY_BYTES bytes",
                    type = TraceType.Error,
                )
                null
            } else {
                record to entropy
            }
        }

        val buffer = ByteBuffer.allocate(HEADER_BYTES + encodable.size * RECORD_BYTES)
        buffer.put(VERSION)
        buffer.putShort(encodable.size.toShort())
        encodable.forEach { (record, entropy) ->
            buffer.put(entropy)
            buffer.putLong(record.creationDate)
            buffer.putLong(record.lastSeen)
            buffer.putLong(record.deletionDate ?: 0L)
        }
        return buffer.array()
    }

    fun decode(bytes: ByteArray): List<AccountRecord> {
        if (bytes.isEmpty()) return emptyList()
        return runCatching {
            val buffer = ByteBuffer.wrap(bytes)
            if (buffer.get() != VERSION) return emptyList()
            val count = buffer.short.toInt()
            if (count < 0 || count > MAX_ACCOUNTS) return emptyList()
            if (bytes.size != HEADER_BYTES + count * RECORD_BYTES) return emptyList()

            (0 until count).map {
                val entropy = ByteArray(ENTROPY_BYTES).also(buffer::get)
                AccountRecord(
                    entropy = entropy.encodeBase64(),
                    creationDate = buffer.long,
                    lastSeen = buffer.long,
                    deletionDate = buffer.long.takeIf { date -> date != 0L },
                )
            }
        }.getOrElse { emptyList() }
    }

    /**
     * Keeps the [MAX_ACCOUNTS] most recently seen, preserving the caller's ordering.
     *
     * Assumes the caller holds no duplicates — entropy is the unique account identifier. Records
     * are matched by structural equality, so an exact duplicate inside the kept window would
     * collapse and return fewer than [MAX_ACCOUNTS].
     */
    fun cap(records: List<AccountRecord>): List<AccountRecord> {
        if (records.size <= MAX_ACCOUNTS) return records
        val keep = records.sortedByDescending { it.lastSeen }.take(MAX_ACCOUNTS).toSet()
        return records.filter { it in keep }
    }
}
