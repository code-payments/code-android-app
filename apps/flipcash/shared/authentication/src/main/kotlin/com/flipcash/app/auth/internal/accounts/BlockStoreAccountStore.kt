package com.flipcash.app.auth.internal.accounts

import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The whole entry is rewritten on every mutation, so concurrent writers would otherwise lose an
 * update. The mutex is the only thing standing between two coroutines and a clobbered list.
 */
internal class BlockStoreAccountStore(
    private val bytes: BlockStoreBytes,
    private val clock: () -> Long = System::currentTimeMillis,
) : AccountStore {

    private val mutex = Mutex()

    override suspend fun all(): List<AccountRecord> =
        allIncludingDeleted().filter { it.isActive }

    override suspend fun allIncludingDeleted(): List<AccountRecord> = mutex.withLock { read() }

    override suspend fun upsert(entropy: String) = mutate { records ->
        val now = clock()
        val existing = records.firstOrNull { it.entropy == entropy }
        if (existing == null) {
            records + AccountRecord(entropy = entropy, creationDate = now, lastSeen = now)
        } else {
            records.map { record ->
                if (record.entropy == entropy) {
                    record.copy(lastSeen = now, deletionDate = null)
                } else {
                    record
                }
            }
        }
    }

    override suspend fun setDeleted(entropy: String, deleted: Boolean) = mutate { records ->
        records.map { record ->
            if (record.entropy == entropy) {
                record.copy(deletionDate = if (deleted) clock() else null)
            } else {
                record
            }
        }
    }

    override suspend fun clear() {
        mutex.withLock { bytes.delete() }
    }

    /**
     * A failed read and an empty store are indistinguishable once decoded, so the write is
     * skipped rather than guessed at. Writing anyway would replace however many accounts are
     * really in the entry with the one record this mutation happens to produce.
     */
    private suspend fun mutate(block: (List<AccountRecord>) -> List<AccountRecord>) {
        mutex.withLock {
            val current = bytes.read() ?: return@withLock
            val written = bytes.write(AccountRecordCodec.encode(block(sorted(current))))
            if (!written) {
                // The Block Store boundary traces the cause; this says what it cost. The list is
                // unchanged, so the caller's account keeps whatever place it already had.
                trace(
                    tag = TAG,
                    message = "Account list write did not land",
                    type = TraceType.Error,
                )
            }
        }
    }

    /** Newest created first, matching how the selection screen lists them. */
    private suspend fun read(): List<AccountRecord> = sorted(bytes.read() ?: ByteArray(0))

    private fun sorted(blob: ByteArray): List<AccountRecord> =
        AccountRecordCodec.decode(blob).sortedByDescending { it.creationDate }

    private companion object {
        const val TAG = "BlockStore"
    }
}
