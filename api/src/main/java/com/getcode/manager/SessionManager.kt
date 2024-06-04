package com.getcode.manager

import com.getcode.ed25519.Ed25519
import com.getcode.generator.OrganizerGenerator
import com.getcode.network.client.Client
import com.getcode.network.client.registerInstallation
import com.getcode.network.client.updatePreferences
import com.getcode.solana.organizer.Organizer
import com.getcode.utils.installationId
import com.getcode.utils.trace
import com.google.firebase.Firebase
import com.google.firebase.installations.installations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SessionManager @Inject constructor(
    private val client: Client,
    private val mnemonicManager: MnemonicManager,
    private val organizerGenerator: OrganizerGenerator,
) {
    data class SessionState(
        val entropyB64: String? = null,
        val keyPair: Ed25519.KeyPair? = null,
        val isAuthenticated: Boolean? = null,
        val isTimelockUnlocked: Boolean = false,
        val organizer: Organizer? = null,
        val userPrefsUpdated: Boolean = false,
    )

    fun set(entropyB64: String) {
        val mnemonic = mnemonicManager.fromEntropyBase64(entropyB64)
        if (getOrganizer()?.mnemonic?.words == mnemonic.words
            && getOrganizer()?.ownerKeyPair == authState.value.keyPair
        ) return

        val organizer = organizerGenerator.generate(mnemonic)

        update {
            SessionState(
                entropyB64 = entropyB64,
                keyPair = organizer.ownerKeyPair,
                isAuthenticated = true,
                organizer = organizer,
            )
        }
    }

    fun clear() {
        trace("Clearing session state")
        update {
            SessionState(entropyB64 = null, keyPair = null, isAuthenticated = false)
        }
    }

    suspend fun comeAlive(): Result<Boolean> {
        val organizer = authState.value.organizer ?: return Result.success(false)
        val installationId = Firebase.installations.installationId()
        if (installationId != null) {
            client.registerInstallation(organizer.ownerKeyPair, installationId)
        }

        return client.updatePreferences(organizer)
            .onSuccess {
                update { it.copy(userPrefsUpdated = true) }
            }
            .onFailure {
                update { it.copy(userPrefsUpdated = true) }
            }
    }

    companion object {
        private val authStateMutable: MutableStateFlow<SessionState> = MutableStateFlow(SessionState())
        val authState: StateFlow<SessionState>
            get() = authStateMutable.asStateFlow()

        fun update(function: (SessionState) -> SessionState) {
            authStateMutable.update(function)
        }

        fun getKeyPair(): Ed25519.KeyPair? = authState.value.keyPair
        fun getOrganizer(): Organizer? = authState.value.organizer
        fun getCurrentBalance() = authState.value.organizer?.availableBalance
        fun isAuthenticated() = authState.value.isAuthenticated

        val entropyB64: String?
            get() = authState.value.entropyB64
    }
}
