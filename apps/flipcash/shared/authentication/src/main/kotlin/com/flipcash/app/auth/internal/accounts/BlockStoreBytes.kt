package com.flipcash.app.auth.internal.accounts

/**
 * A single keyed blob in a store that outlives the app sandbox.
 *
 * Exists so that everything above it is testable without Play services, and so that the one file
 * touching `BlockstoreClient` stays small.
 */
internal interface BlockStoreBytes {
    /**
     * The stored blob, an empty array when nothing is stored, or `null` when the store could
     * not be read. A caller about to rewrite the blob must treat `null` as "do not write" —
     * folding it into "empty" would rewrite a list it never saw.
     */
    suspend fun read(): ByteArray?

    /** Returns false when the write did not happen. */
    suspend fun write(bytes: ByteArray): Boolean

    suspend fun delete()
}
