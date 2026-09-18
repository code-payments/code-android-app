package com.flipcash.app.auth.internal.accounts

/**
 * Seeds Block Store from the pre-existing local DataStore.
 *
 * A condition rather than a one-shot upgrade hook: it runs on every launch and does nothing once
 * the store is non-empty. Written that way it also recovers a corrupt or unreadable entry, which
 * decodes as empty.
 */
internal object AccountStoreMigration {
    suspend fun run(store: AccountStore, legacyEntropy: suspend () -> List<String>) {
        if (store.allIncludingDeleted().isNotEmpty()) return
        legacyEntropy().forEach { store.upsert(it) }
    }
}
