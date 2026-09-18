package com.flipcash.app.auth.internal.accounts

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountStoreMigrationTest {

    /** Records what the production code asks of the store, without Block Store or Play services. */
    private class RecordingAccountStore : AccountStore {
        val records = mutableListOf<AccountRecord>()
        override suspend fun all(): List<AccountRecord> = records.filter { it.isActive }
        override suspend fun allIncludingDeleted(): List<AccountRecord> = records
        override suspend fun upsert(entropy: String) {
            if (records.none { it.entropy == entropy }) {
                records += AccountRecord(entropy, creationDate = 1L, lastSeen = 1L)
            } else {
                // Mirrors BlockStoreAccountStore, which bumps lastSeen and undeletes on upsert
                // of a known account.
                records.replaceAll { record ->
                    if (record.entropy == entropy) {
                        record.copy(lastSeen = record.lastSeen + 1, deletionDate = null)
                    } else {
                        record
                    }
                }
            }
        }
        override suspend fun setDeleted(entropy: String, deleted: Boolean) {
            records.replaceAll { record ->
                if (record.entropy == entropy) record.copy(deletionDate = if (deleted) 2L else null)
                else record
            }
        }
        override suspend fun clear() = records.clear()
    }

    @Test
    fun `seeds an empty store from the legacy entropy`() = runTest {
        val store = RecordingAccountStore()

        AccountStoreMigration.run(store, legacyEntropy = { listOf("legacy-entropy") })

        assertEquals(listOf("legacy-entropy"), store.all().map { it.entropy })
    }

    @Test
    fun `is a no-op when the store already has accounts`() = runTest {
        val store = RecordingAccountStore()
        store.upsert("existing")

        AccountStoreMigration.run(store, legacyEntropy = { listOf("legacy-entropy") })

        assertEquals(listOf("existing"), store.all().map { it.entropy })
    }

    @Test
    fun `is a no-op when there is no legacy entropy`() = runTest {
        val store = RecordingAccountStore()

        AccountStoreMigration.run(store, legacyEntropy = { emptyList() })

        assertTrue(store.all().isEmpty())
    }

    /**
     * Pins the guard to `allIncludingDeleted()`. A store holding only a removed account has
     * already run, so seeding it again would resurrect the account the user deleted.
     */
    @Test
    fun `is a no-op when the store holds only a deleted account`() = runTest {
        val store = RecordingAccountStore()
        store.upsert("removed")
        store.setDeleted("removed", deleted = true)

        AccountStoreMigration.run(store, legacyEntropy = { listOf("removed") })

        assertTrue(store.all().isEmpty())
        assertEquals(listOf("removed"), store.allIncludingDeleted().map { it.entropy })
    }
}
