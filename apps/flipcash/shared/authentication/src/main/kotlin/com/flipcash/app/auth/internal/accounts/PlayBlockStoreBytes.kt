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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
 * is left as a breadcrumb instead of being reported. Cancellation of the calling coroutine is not
 * a failure and is rethrown.
 */
@Singleton
internal class PlayBlockStoreBytes @Inject constructor(
    @ApplicationContext context: Context,
) : BlockStoreBytes {

    private val client = Blockstore.getClient(context)

    /**
     * Set once a read fails as unsupported, so the rest of the process skips the call. Play services
     * gates the feature by rollout as well as version, so there is no reliable check up front. Writes
     * and deletes use different features and are not gated by this.
     */
    @Volatile
    private var readUnsupported = false

    override suspend fun read(): ByteArray? {
        if (readUnsupported) return null
        return runCatching {
            val request = RetrieveBytesRequest.Builder()
                .setKeys(listOf(KEY))
                .build()
            client.retrieveBytes(request).await()
                .blockstoreDataMap[KEY]
                ?.bytes
                ?: ByteArray(0)
        }.getOrElse { error ->
            currentCoroutineContext().ensureActive()
            if (error is UnsupportedApiCallException) readUnsupported = true
            traceFailure("Block Store read failed", error)
            null
        }
    }

    override suspend fun write(bytes: ByteArray): Boolean = runCatching {
        // Without the cancellation check, a cancel here would fall through to storeBytes with backup
        // off, and that starts in Play services before the await below can notice.
        val canEncrypt = runCatching { client.isEndToEndEncryptionAvailable.await() }
            .getOrElse {
                currentCoroutineContext().ensureActive()
                false
            }

        val data = StoreBytesData.Builder()
            .setKey(KEY)
            .setBytes(bytes)
            .setShouldBackupToCloud(canEncrypt)
            .build()

        client.storeBytes(data).await()
        true
    }.getOrElse { error ->
        currentCoroutineContext().ensureActive()
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
            currentCoroutineContext().ensureActive()
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
