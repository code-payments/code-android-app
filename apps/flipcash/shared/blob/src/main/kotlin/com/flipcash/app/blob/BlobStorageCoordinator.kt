package com.flipcash.app.blob

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.BlobStorageController
import com.flipcash.services.models.InitiateExternalUploadError
import com.flipcash.services.models.blob.EncryptedConstraints
import com.flipcash.services.models.blob.MimeTypeConstraints
import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.ChatId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * App-layer facade over blob storage ([BlobStorageController]). ViewModels use this coordinator
 * rather than the service-layer controller, mirroring the app's Coordinator → Controller pattern
 * (e.g. [ChatCoordinator][com.flipcash.shared.chat.ChatCoordinator]).
 *
 * It also owns the persisted [UploadPolicy] cache, which honours the policy's own `ttl` and
 * `version`: [preloadPolicy] is called on launch by the session controller, [policy] re-fetches
 * when the cached copy has aged past its ttl, and [upload] re-fetches when the server rejects an
 * upload on policy grounds (a version-mismatch signal).
 */
@Singleton
class BlobStorageCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val blobStorageController: BlobStorageController,
    dispatchers: DispatcherProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.IO)

    private val dataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("upload-policy") }
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val refreshing = AtomicBoolean(false)

    /**
     * The upload policy, kept fresh. Emits the cached value and, whenever that value is stale (past
     * its `ttl`) or absent, triggers a background refresh whose result re-emits here. Observe it
     * directly from a ViewModel; a stale cache resolves to a fresh value without a manual fetch.
     */
    val policy: Flow<UploadPolicy?> = dataStore.data
        .map { it.decode() }
        .onEach { cached -> if (cached == null || !cached.isFresh()) triggerRefresh() }
        .map { it?.toDomain() }

    /** Fetches the latest upload policy and caches it (stamped with the fetch time). */
    suspend fun preloadPolicy(): Result<UploadPolicy> =
        blobStorageController.getUploadPolicy().onSuccess { persist(it) }

    /**
     * Uploads [bytes] to storage and returns the READY [BlobId] — reserve, PUT/POST, complete, and
     * poll are all handled inside the controller. A policy-driven rejection invalidates the cached
     * policy (the server echoes a newer policy version on such denials).
     */
    suspend fun upload(
        bytes: ByteArray,
        mimeType: String,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): Result<BlobId> {
        val result = blobStorageController.upload(bytes, mimeType, onProgress)
        result.exceptionOrNull()?.let { refreshIfPolicyChanged(it) }
        return result
    }

    /**
     * Stores an encoded chat photo and returns its READY [BlobId], retrying what a retry can fix.
     *
     * [jpeg] is the encoder's output. With [sealing] it is encrypted for that chat and uploaded as
     * opaque bytes; without, it goes up as plain `image/jpeg` for the server to moderate. Each
     * attempt reserves afresh, so a retry gets a new blob id and re-seals under it.
     *
     * Up to [ChatMediaRetry.BACKOFFS].size retries, 1 s, 2 s, then 4 s apart, for failures in
     * transit or while finalizing; a refusal that repeating can't change (see
     * [ChatMediaRetry.isRetryable]) is returned at once. [onProgress] restarts from zero on a retry.
     */
    suspend fun storeChatMedia(
        jpeg: ByteArray,
        sealing: ChatMediaSealing? = null,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): Result<BlobId> = withStoreRetries { _ ->
        if (sealing != null) {
            blobStorageController.uploadSealed(jpeg, sealing.chatId, sealing.seal, onProgress)
        } else {
            blobStorageController.uploadChatMedia(jpeg, ChatMediaEncoder.UPLOAD_MIME_TYPE, onProgress)
        }
    }

    /**
     * [storeChatMedia] up to the point the bytes are in storage, without waiting for finalization.
     * Retries only what happens before that, so a returned id means "stored": a caller that then
     * fails to see it finalize re-polls it with [awaitChatMediaReady] instead of uploading again.
     * [onAttempt] runs before each attempt, first included, so progress can restart with a retry.
     */
    suspend fun storeChatMediaUnfinalized(
        jpeg: ByteArray,
        sealing: ChatMediaSealing? = null,
        onAttempt: (() -> Unit)? = null,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): Result<BlobId> = withStoreRetries { _ ->
        onAttempt?.invoke()
        if (sealing != null) {
            blobStorageController.storeSealed(jpeg, sealing.chatId, sealing.seal, onProgress)
        } else {
            blobStorageController.storeChatMedia(jpeg, ChatMediaEncoder.UPLOAD_MIME_TYPE, onProgress)
        }
    }

    /** Waits for a blob [storeChatMediaUnfinalized] stored to finalize. */
    suspend fun awaitChatMediaReady(blobId: BlobId): Result<BlobId> =
        blobStorageController.awaitChatMediaReady(blobId)

    private suspend fun withStoreRetries(attemptStore: suspend (attempt: Int) -> Result<BlobId>): Result<BlobId> {
        var attempt = 0
        while (true) {
            val result = attemptStore(attempt)
            val failure = result.exceptionOrNull() ?: return result

            refreshIfPolicyChanged(failure)
            if (!ChatMediaRetry.isRetryable(failure) || attempt >= ChatMediaRetry.BACKOFFS.size) return result
            delay(ChatMediaRetry.BACKOFFS[attempt++])
        }
    }

    suspend fun reset() {
        dataStore.edit { it.remove(KEY_UPLOAD_POLICY) }
    }

    // Fire-and-forget refresh, deduped so a burst of stale emissions launches at most one fetch.
    private fun triggerRefresh() {
        if (refreshing.compareAndSet(false, true)) {
            scope.launch {
                try {
                    preloadPolicy()
                } finally {
                    refreshing.set(false)
                }
            }
        }
    }

    // A policy-driven denial means our cached policy let something through the server now rejects.
    // Re-fetch when the echoed version differs from what we cached (or we have nothing cached).
    private suspend fun refreshIfPolicyChanged(cause: Throwable) {
        val deniedVersion = when (cause) {
            is InitiateExternalUploadError.UnsupportedType -> cause.policyVersion
            is InitiateExternalUploadError.TooLarge -> cause.policyVersion
            else -> return
        }
        if (deniedVersion == null || deniedVersion != cached()?.version) {
            preloadPolicy()
        }
    }

    private suspend fun persist(policy: UploadPolicy) {
        val raw = json.encodeToString(CachedUploadPolicy.fromDomain(policy, now()))
        dataStore.edit { it[KEY_UPLOAD_POLICY] = raw }
    }

    private suspend fun cached(): CachedUploadPolicy? = dataStore.data.first().decode()

    private fun Preferences.decode(): CachedUploadPolicy? =
        this[KEY_UPLOAD_POLICY]?.let { raw ->
            runCatching { json.decodeFromString<CachedUploadPolicy>(raw) }.getOrNull()
        }

    private fun CachedUploadPolicy.isFresh(): Boolean = now() - fetchedAtMillis < ttlMillis

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        // v2: entries cached before the policy carried `encrypted` would read back as "encrypted
        // uploads not allowed" for the rest of their ttl; a new key makes them refetch instead.
        private val KEY_UPLOAD_POLICY = stringPreferencesKey("cached_upload_policy_v2")
    }
}

/**
 * On-disk form. [UploadPolicy.ttl] is a [kotlin.time.Duration] (not kotlinx-serializable) so it is
 * stored as milliseconds; [fetchedAtMillis] stamps when it was cached so freshness can be checked
 * against the ttl; [MimeTypeConstraints] and [EncryptedConstraints] are already `@Serializable` and stored as-is.
 */
@kotlinx.serialization.Serializable
private data class CachedUploadPolicy(
    val version: String,
    val ttlMillis: Long,
    val fetchedAtMillis: Long,
    val mimeTypeConstraints: List<MimeTypeConstraints>,
    // Absent in entries cached before encrypted uploads existed, which read back as "not allowed"
    // until the next refresh.
    val encrypted: EncryptedConstraints? = null,
) {
    fun toDomain(): UploadPolicy = UploadPolicy(
        version = version,
        ttl = ttlMillis.milliseconds,
        mimeTypeConstraints = mimeTypeConstraints,
        encrypted = encrypted,
    )

    companion object {
        fun fromDomain(policy: UploadPolicy, fetchedAtMillis: Long): CachedUploadPolicy = CachedUploadPolicy(
            version = policy.version,
            ttlMillis = policy.ttl.inWholeMilliseconds,
            fetchedAtMillis = fetchedAtMillis,
            mimeTypeConstraints = policy.mimeTypeConstraints,
            encrypted = policy.encrypted,
        )
    }
}
