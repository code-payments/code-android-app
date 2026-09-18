package com.flipcash.app.auth.internal.accounts

/**
 * The durable account list. Replaces the Google Password Manager half of
 * [com.flipcash.app.auth.internal.credentials.PassphraseCredentialManager], which could restore an
 * account but never enumerate them.
 *
 * Writes are best effort. Play services being unavailable costs an account its place in the
 * list, not its funds — the access key is still the user's backstop — and the failure is traced
 * at the Block Store boundary. A mutation never rewrites a list it could not first read, so a
 * failure can drop an addition but cannot lose an existing entry.
 */
interface AccountStore {
    /** Active accounts, newest created first. Empty when the store is unavailable. */
    suspend fun all(): List<AccountRecord>

    /** Every record, including soft-deleted ones. */
    suspend fun allIncludingDeleted(): List<AccountRecord>

    /** Inserts the account, or bumps its `lastSeen` and undeletes it if it is already known. */
    suspend fun upsert(entropy: String)

    suspend fun setDeleted(entropy: String, deleted: Boolean)

    /** Wipes the entry. Account removal uses [setDeleted]; this is for a full reset. */
    suspend fun clear()
}
