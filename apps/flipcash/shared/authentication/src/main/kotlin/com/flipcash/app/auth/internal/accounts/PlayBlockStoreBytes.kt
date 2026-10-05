package com.flipcash.app.auth.internal.accounts

import android.content.Context
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.google.android.gms.common.api.UnsupportedApiCallException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Block Store keeps this blob in Play services' own directory rather than the app sandbox, so it
 * survives uninstall when the user has Backup services on.
 *
 * Cloud backup is requested only when Play services reports end-to-end encryption is available,
 * which needs a screen lock. The flag is recomputed on every write rather than cached, because
 * leaving it unset deletes previously backed-up cloud data on the next sync.
 *
 * Every Play services failure is swallowed: no Play services means no durable list, not a broken
 * login. A Play services build too old for these calls is expected on some devices, so that case
 * is left as a breadcrumb instead of being reported.
 */
@Singleton
internal class PlayBlockStoreBytes @Inject constructor(
    @ApplicationContext context: Context,
) : BlockStoreBytes {

    private val client = Blockstore.getClient(context)

    override suspend fun read(): ByteArray? = runCatching {
        val request = RetrieveBytesRequest.Builder()
            .setKeys(listOf(KEY))
            .build()
        client.retrieveBytes(request).await()
            .blockstoreDataMap[KEY]
            ?.bytes
            ?: ByteArray(0)
    }.getOrElse { error ->
        traceFailure("Block Store read failed", error)
        null
    }

    override suspend fun write(bytes: ByteArray): Boolean = runCatching {
        val canEncrypt = runCatching { client.isEndToEndEncryptionAvailable.await() }
            .getOrDefault(false)

        val data = StoreBytesData.Builder()
            .setKey(KEY)
            .setBytes(bytes)
            .setShouldBackupToCloud(canEncrypt)
            .build()

        client.storeBytes(data).await()
        true
    }.getOrElse { error ->
        traceFailure("Block Store write failed", error)
        false
    }

    override suspend fun delete() {
        runCatching {
            val request = DeleteBytesRequest.Builder()
                .setKeys(listOf(KEY))
                .build()
            client.deleteBytes(request).await()
        }.onFailure { error ->
            traceFailure("Block Store delete failed", error)
        }
    }

    private fun traceFailure(message: String, error: Throwable) {
        if (error is UnsupportedApiCallException) {
            trace(tag = TAG, message = "$message: unsupported by Play services", type = TraceType.Log)
        } else {
            trace(tag = TAG, message = message, error = error, type = TraceType.Error)
        }
    }

    private companion object {
        const val TAG = "BlockStore"

        /**
         * One of Block Store's 16 entries. The whole list lives here so a mutation is one atomic
         * write with no cross-entry consistency to manage.
         */
        const val KEY = "com.flipcash.account.list"
    }
}
