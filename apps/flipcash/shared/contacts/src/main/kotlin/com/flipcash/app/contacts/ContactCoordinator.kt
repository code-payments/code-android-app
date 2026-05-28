@file:OptIn(ExperimentalCoroutinesApi::class)

package com.flipcash.app.contacts

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.flipcash.app.contacts.device.DeviceContact
import com.flipcash.app.contacts.device.ScopeAwareContactReader
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
) : SessionListener, DefaultLifecycleObserver {

    companion object {
        private const val TAG = "ContactCoordinator"
    }

    data class ContactState(
        val contacts: Map<String, DeviceContact> = emptyMap(),
        val flipcashE164s: Set<String> = emptySet(),
        val syncState: SyncState = SyncState.Idle,
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
        sync()
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
                sync()
            }
            .launchIn(scope)
    }

    override fun onStart(owner: LifecycleOwner) {
        if (cluster.value != null) {
            trace(tag = TAG, message = "Lifecycle resumed, triggering contact sync", type = TraceType.Process)
            sync()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        syncJob?.cancel()
    }

    // endregion

    // region Public API

    fun sync() {
        syncJob?.cancel()
        syncJob = scope.launch { performSync() }
    }

    fun addPickedContacts(contactIds: List<Long>) {
        contactReader.addSelectedContacts(contactIds)
        sync()
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

    // endregion

    // region Internal

    private suspend fun hydrateFromPersistence() {
        val db = FlipcashDatabase.getInstance() ?: return
        val mappings = db.contactDao().getAllMappings()
        if (mappings.isEmpty()) return

        val contacts = mappings.associate { mapping ->
            mapping.e164 to DeviceContact(
                e164 = mapping.e164,
                androidContactId = mapping.androidContactId,
                displayName = mapping.displayName,
                photoUri = mapping.photoUri,
            )
        }
        val flipcashE164s = mappings.filter { it.isOnFlipcash }.map { it.e164 }.toSet()

        _state.update {
            it.copy(contacts = contacts, flipcashE164s = flipcashE164s)
        }

        trace(tag = TAG, message = "Hydrated ${mappings.size} contacts from persistence", type = TraceType.Process)
    }

    private suspend fun performSync() {
        if (cluster.value == null) return

        _state.update { it.copy(syncState = SyncState.Syncing) }

        try {
            // 1. Read device contacts
            val deviceContacts = contactReader.readAll().getOrElse { error ->
                trace(tag = TAG, message = "Cannot read contacts: ${error.message}", type = TraceType.Log)
                return
            }

            if (deviceContacts.isEmpty()) {
                trace(tag = TAG, message = "No device contacts found", type = TraceType.Process)
                _state.update { it.copy(syncState = SyncState.Synced) }
                return
            }

            // 2. Compute checksum
            val newChecksum = ContactChecksum.compute(deviceContacts.keys)

            // 3. Diff against persisted mappings
            val db = FlipcashDatabase.getInstance() ?: run {
                _state.update { it.copy(syncState = SyncState.Error) }
                return
            }
            val dao = db.contactDao()
            val existingMappings = dao.getAllMappings()
            val existingE164s = existingMappings.map { it.e164 }.toSet()
            val newE164s = deviceContacts.keys

            val adds = newE164s - existingE164s
            val removes = existingE164s - newE164s

            // 4. Persist new mappings
            if (adds.isNotEmpty()) {
                val newEntities = adds.mapNotNull { e164 ->
                    deviceContacts[e164]?.let { contact ->
                        ContactMappingEntity(
                            e164 = contact.e164,
                            androidContactId = contact.androidContactId,
                            displayName = contact.displayName,
                            photoUri = contact.photoUri,
                        )
                    }
                }
                dao.upsertMappings(newEntities)
            }
            if (removes.isNotEmpty()) {
                dao.deleteMappings(removes.toList())
            }

            // Update in-memory contacts
            _state.update { it.copy(contacts = deviceContacts) }

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

            _state.update { it.copy(syncState = SyncState.Synced) }
            trace(tag = TAG, message = "Contact sync complete", type = TraceType.Process)

        } catch (e: Exception) {
            trace(tag = TAG, message = "Contact sync failed: ${e.message}", error = e, type = TraceType.Error)
            _state.update { it.copy(syncState = SyncState.Error) }
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
