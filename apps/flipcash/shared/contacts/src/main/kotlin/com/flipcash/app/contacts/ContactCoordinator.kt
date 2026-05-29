@file:OptIn(ExperimentalCoroutinesApi::class)

package com.flipcash.app.contacts

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.flipcash.app.contacts.device.DeviceContact
import com.flipcash.app.contacts.device.PickedContactData
import com.flipcash.app.contacts.device.ScopeAwareContactReader
import com.flipcash.app.phone.PhoneUtils
import com.flipcash.app.contacts.sync.ContactChecksum
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.ContactMappingEntity
import com.flipcash.app.persistence.entities.ContactSyncStateEntity
import com.flipcash.services.controllers.ContactListController
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.CheckSyncError
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.DeltaUploadError
import com.flipcash.services.models.GetContactsError
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.providers.SessionListener
import com.getcode.solana.keys.Checksum
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.TraceType
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactCoordinator @Inject constructor(
    private val contactListController: ContactListController,
    private val resolverController: ResolverController,
    private val networkObserver: NetworkConnectivityListener,
    private val contactReader: ScopeAwareContactReader,
    private val phoneUtils: PhoneUtils,
) : SessionListener, DefaultLifecycleObserver {

    companion object {
        private const val TAG = "ContactCoordinator"
    }

    data class ContactState(
        val contacts: Map<String, DeviceContact> = emptyMap(),
        val flipcashE164s: Set<String> = emptySet(),
        val syncState: SyncState = SyncState.Idle,
        val hasEverSynced: Boolean = false,
    )

    enum class SyncState { Idle, Syncing, Synced, Error }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cluster = MutableStateFlow<AccountCluster?>(null)
    private val _state = MutableStateFlow(ContactState())
    private var syncJob: Job? = null

    val state: StateFlow<ContactState>
        get() = _state.asStateFlow()

    // region SessionListener

    override suspend fun onUserLoggedIn(cluster: AccountCluster) {
        trace(tag = TAG, message = "User logged in, hydrating contacts", type = TraceType.User)
        this.cluster.value = cluster
        hydrateFromPersistence()
        launchSync()
    }

    // endregion

    // region Lifecycle

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        cluster.filterNotNull()
            .flatMapLatest { networkObserver.state }
            .distinctUntilChanged()
            .filter { it.connected }
            .onEach {
                trace(tag = TAG, message = "Network connected, triggering contact sync", type = TraceType.Process)
                launchSync()
            }
            .launchIn(scope)
    }

    override fun onStart(owner: LifecycleOwner) {
        if (cluster.value != null) {
            scope.launch { clearServerContactSetIfRevoked() }
            trace(tag = TAG, message = "Lifecycle resumed, triggering contact sync", type = TraceType.Process)
            launchSync()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        syncJob?.cancel()
    }

    // endregion

    // region Public API

    suspend fun sync(): Result<Unit> = performSync()

    private fun launchSync() {
        syncJob?.cancel()
        syncJob = scope.launch { performSync() }
    }

    suspend fun addPickedContacts(contacts: List<PickedContactData>): Result<Unit> {
        contactReader.addSelectedContacts(contacts)
        return performSync()
    }

    suspend fun removeContact(e164: String) {
        contactReader.removeSelectedContact(e164)
        val db = FlipcashDatabase.getInstance() ?: return
        db.contactDao().deleteMappings(listOf(e164))
        _state.update { state ->
            state.copy(
                contacts = state.contacts - e164,
                flipcashE164s = state.flipcashE164s - e164,
            )
        }
    }

    suspend fun resolve(e164: String): Result<PublicKey> {
        return resolverController.resolve(ContactMethod.Phone(e164))
    }

    suspend fun reset() {
        syncJob?.cancel()
        _state.value = ContactState()
        cluster.value = null
        contactReader.reset()
        val db = FlipcashDatabase.getInstance() ?: return
        db.contactDao().clearAll()
        trace(tag = TAG, message = "reset complete", type = TraceType.Process)
    }

    /**
     * Detects a contacts-permission revoke and wipes the server's stored
     * contact set. A non-null checksum means we previously uploaded; if
     * READ_CONTACTS is now denied, wipe the server set. Idempotent: a
     * successful wipe clears the checksum; a failure leaves it intact so
     * the next foreground retries.
     */
    suspend fun clearServerContactSetIfRevoked() {
        val db = FlipcashDatabase.getInstance() ?: return
        val syncState = db.contactDao().getSyncState() ?: return
        if (syncState.checksumBytes.all { it == 0.toByte() }) return

        if (!contactReader.isPermissionRevoked()) return

        clearServerContactSet()
        _state.value = ContactState()
        contactReader.reset()
        db.contactDao().clearAll()
        trace(tag = TAG, message = "Cleared server contact set after permission revoke", type = TraceType.Process)
    }

    /**
     * Sends an empty full upload to wipe the server-side contact set.
     * Best-effort — failures are logged but not propagated.
     * Must be called while the session is still authenticated.
     */
    suspend fun clearServerContactSet() {
        try {
            val emptyChecksum = ContactChecksum.compute(emptySet())
            contactListController.fullUpload(
                phones = kotlinx.coroutines.flow.flowOf(emptyList()),
                expectedChecksum = emptyChecksum,
            )
        } catch (e: Exception) {
            trace(tag = TAG, message = "Failed to clear server contact set: ${e.message}", type = TraceType.Error)
        }
    }

    // endregion

    // region Internal

    private suspend fun hydrateFromPersistence() {
        val db = FlipcashDatabase.getInstance() ?: return
        val syncState = db.contactDao().getSyncState()
        val mappings = db.contactDao().getAllMappings()

        val hasEverSynced = syncState != null || mappings.isNotEmpty()
        if (mappings.isEmpty()) {
            if (hasEverSynced) {
                _state.update { it.copy(hasEverSynced = true) }
            }
            return
        }

        val contacts = mappings.associate { mapping ->
            mapping.e164 to DeviceContact(
                e164 = mapping.e164,
                androidContactId = mapping.androidContactId,
                displayName = mapping.displayName,
                photoUri = mapping.photoUri,
                displayNumber = mapping.displayNumber,
            )
        }
        val flipcashE164s = mappings.filter { it.isOnFlipcash }.map { it.e164 }.toSet()

        _state.update {
            it.copy(contacts = contacts, flipcashE164s = flipcashE164s, hasEverSynced = true)
        }

        trace(tag = TAG, message = "Hydrated ${mappings.size} contacts from persistence", type = TraceType.Process)
    }

    private suspend fun performSync(): Result<Unit> {
        if (cluster.value == null) return Result.failure(IllegalStateException("No active session"))

        _state.update { it.copy(syncState = SyncState.Syncing) }

        try {
            // 1. Read device contacts
            val deviceContacts = contactReader.readAll().getOrElse { error ->
                trace(tag = TAG, message = "Cannot read contacts: ${error.message}", type = TraceType.Log)
                return Result.failure(error)
            }

            if (deviceContacts.isEmpty()) {
                trace(tag = TAG, message = "No device contacts found", type = TraceType.Process)
                _state.update { it.copy(syncState = SyncState.Synced) }
                return Result.success(Unit)
            }

            // 2. Compute checksum
            val newChecksum = ContactChecksum.compute(deviceContacts.keys)

            // 3. Diff against persisted mappings
            val db = FlipcashDatabase.getInstance() ?: run {
                _state.update { it.copy(syncState = SyncState.Error) }
                return Result.failure(IllegalStateException("Database unavailable"))
            }
            val dao = db.contactDao()
            val existingMappings = dao.getAllMappings()
            val existingE164s = existingMappings.map { it.e164 }.toSet()
            val newE164s = deviceContacts.keys

            val adds = newE164s - existingE164s
            val removes = existingE164s - newE164s

            // 4. Persist all mappings (upsert fixes metadata staleness for name/photo changes)
            val allEntities = deviceContacts.values.map { contact ->
                ContactMappingEntity(
                    e164 = contact.e164,
                    androidContactId = contact.androidContactId,
                    displayName = contact.displayName,
                    photoUri = contact.photoUri,
                    displayNumber = phoneUtils.formatNumber(contact.e164),
                )
            }
            dao.upsertMappings(allEntities)
            if (removes.isNotEmpty()) {
                dao.deleteMappings(removes.toList())
            }

            // Update in-memory contacts with displayNumber, merging into existing state
            // so persisted contacts aren't lost when the picker returns only new picks.
            val enrichedContacts = deviceContacts.mapValues { (_, contact) ->
                contact.copy(displayNumber = phoneUtils.formatNumber(contact.e164))
            }
            _state.update { it.copy(contacts = it.contacts + enrichedContacts) }

            // 5. CheckSync with server
            val syncState = dao.getSyncState()
            val oldChecksum = syncState?.let { Checksum(it.checksumBytes.toList()) }

            val checkSyncResult = contactListController.checkSync(newChecksum)

            checkSyncResult.fold(
                onSuccess = { serverChecksum ->
                    // Checksums match — skip upload
                    trace(tag = TAG, message = "Contacts in sync with server", type = TraceType.Process)
                    persistSyncState(dao, newChecksum)
                },
                onFailure = { error ->
                    when (error) {
                        is CheckSyncError.OutOfSync -> {
                            // Try delta upload first
                            if (oldChecksum != null && adds.isNotEmpty() || removes.isNotEmpty()) {
                                val deltaResult = contactListController.deltaUpload(
                                    adds = adds.map { ContactMethod.Phone(it) },
                                    removes = removes.map { ContactMethod.Phone(it) },
                                    oldChecksum = oldChecksum ?: newChecksum,
                                    newChecksum = newChecksum,
                                )
                                deltaResult.fold(
                                    onSuccess = {
                                        trace(tag = TAG, message = "Delta upload successful", type = TraceType.Process)
                                        persistSyncState(dao, newChecksum)
                                    },
                                    onFailure = { deltaError ->
                                        if (deltaError is DeltaUploadError.ChecksumDrift || deltaError is DeltaUploadError.ChecksumMismatch) {
                                            performFullUpload(newE164s, newChecksum, dao)
                                        } else {
                                            trace(tag = TAG, message = "Delta upload failed: ${deltaError.message}", type = TraceType.Error)
                                        }
                                    }
                                )
                            } else {
                                performFullUpload(newE164s, newChecksum, dao)
                            }
                        }
                        else -> {
                            // First sync or other error — full upload
                            performFullUpload(newE164s, newChecksum, dao)
                        }
                    }
                }
            )

            // 6. GetFlipcashContacts
            fetchFlipcashContacts(newChecksum, dao)

            _state.update { it.copy(syncState = SyncState.Synced, hasEverSynced = true) }
            trace(tag = TAG, message = "Contact sync complete", type = TraceType.Process)
            return Result.success(Unit)

        } catch (e: Exception) {
            trace(tag = TAG, message = "Contact sync failed: ${e.message}", error = e, type = TraceType.Error)
            _state.update { it.copy(syncState = SyncState.Error) }
            return Result.failure(e)
        }
    }

    private suspend fun performFullUpload(
        e164s: Set<String>,
        checksum: Checksum,
        dao: com.flipcash.app.persistence.dao.ContactDao,
    ) {
        val phones = e164s.map { ContactMethod.Phone(it) }
        val chunked = phones.chunked(500)

        val result = contactListController.fullUpload(
            phones = kotlinx.coroutines.flow.flowOf(*chunked.toTypedArray()),
            expectedChecksum = checksum,
        )

        result.fold(
            onSuccess = {
                trace(tag = TAG, message = "Full upload successful (${e164s.size} contacts)", type = TraceType.Process)
                persistSyncState(dao, checksum)
            },
            onFailure = { error ->
                trace(tag = TAG, message = "Full upload failed: ${error.message}", type = TraceType.Error)
            }
        )
    }

    private suspend fun persistSyncState(
        dao: com.flipcash.app.persistence.dao.ContactDao,
        checksum: Checksum,
    ) {
        dao.upsertSyncState(
            ContactSyncStateEntity(
                checksumBytes = checksum.byteArray,
                lastSyncTimestamp = System.currentTimeMillis(),
            )
        )
    }

    private suspend fun fetchFlipcashContacts(
        checksum: Checksum,
        dao: com.flipcash.app.persistence.dao.ContactDao,
    ) {
        try {
            val result = contactListController.getFlipcashContacts(checksum)
                .firstOrNull()

            result?.onSuccess { phones ->
                val flipcashE164s = phones.map { it.phoneNumber }.toSet()
                dao.clearFlipcashStatus()
                if (flipcashE164s.isNotEmpty()) {
                    dao.markAsFlipcash(flipcashE164s.toList())
                }
                _state.update { it.copy(flipcashE164s = flipcashE164s) }
                trace(tag = TAG, message = "Found ${flipcashE164s.size} contacts on Flipcash", type = TraceType.Process)
            }?.onFailure { error ->
                if (error is GetContactsError.NotFound) {
                    dao.clearFlipcashStatus()
                    _state.update { it.copy(flipcashE164s = emptySet()) }
                    trace(tag = TAG, message = "No contacts on Flipcash yet", type = TraceType.Process)
                } else {
                    trace(tag = TAG, message = "GetFlipcashContacts failed: ${error.message}", type = TraceType.Error)
                }
            }
        } catch (e: Exception) {
            trace(tag = TAG, message = "GetFlipcashContacts exception: ${e.message}", error = e, type = TraceType.Error)
        }
    }

    // endregion
}

val LocalContactCoordinator = staticCompositionLocalOf<ContactCoordinator> {
    error("No ContactCoordinator provided")
}
