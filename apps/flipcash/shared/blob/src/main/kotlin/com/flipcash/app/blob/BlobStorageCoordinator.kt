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
import com.flipcash.services.models.blob.MimeTypeConstraints
import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.services.models.chat.BlobId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
    suspend fun upload(bytes: ByteArray, mimeType: String): Result<BlobId> {
        val result = blobStorageController.upload(bytes, mimeType)
        result.exceptionOrNull()?.let { refreshIfPolicyChanged(it) }
        return result
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
        private val KEY_UPLOAD_POLICY = stringPreferencesKey("cached_upload_policy")
    }
}

/**
 * On-disk form. [UploadPolicy.ttl] is a [kotlin.time.Duration] (not kotlinx-serializable) so it is
 * stored as milliseconds; [fetchedAtMillis] stamps when it was cached so freshness can be checked
 * against the ttl; [MimeTypeConstraints] is already `@Serializable` and stored as-is.
 */
@kotlinx.serialization.Serializable
private data class CachedUploadPolicy(
    val version: String,
    val ttlMillis: Long,
    val fetchedAtMillis: Long,
    val mimeTypeConstraints: List<MimeTypeConstraints>,
) {
    fun toDomain(): UploadPolicy = UploadPolicy(
        version = version,
        ttl = ttlMillis.milliseconds,
        mimeTypeConstraints = mimeTypeConstraints,
    )

    companion object {
        fun fromDomain(policy: UploadPolicy, fetchedAtMillis: Long): CachedUploadPolicy = CachedUploadPolicy(
            version = policy.version,
            ttlMillis = policy.ttl.inWholeMilliseconds,
            fetchedAtMillis = fetchedAtMillis,
            mimeTypeConstraints = policy.mimeTypeConstraints,
        )
    }
}
