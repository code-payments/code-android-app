package com.getcode.manager

import android.content.Context
import com.getcode.crypt.MnemonicPhrase
import com.getcode.ed25519.Ed25519
import com.getcode.solana.organizer.Organizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SessionManager @Inject constructor(
) {
    data class SessionState(
        val entropyB64: String? = null,
        val keyPair: Ed25519.KeyPair? = null,
        val isAuthenticated: Boolean? = null,
        val isTimelockUnlocked: Boolean = false,
        val organizer: Organizer? = null
    )

    fun set(context: Context, entropyB64: String) {
        val mnemonic = MnemonicPhrase.fromEntropyB64(context, entropyB64)
        if (getOrganizer()?.mnemonic?.words == mnemonic.words
            && getOrganizer()?.ownerKeyPair == authState.value?.keyPair
        ) return
        val organizer = Organizer.newInstance(
            context = context,
            mnemonic = mnemonic
        )

        update {
            SessionState(
                entropyB64 = entropyB64,
                keyPair = organizer.ownerKeyPair,
                isAuthenticated = true,
                organizer = organizer
            )
        }
    }

    fun clear() {
        Timber.d("Clearing session state")
        update {
            SessionState(entropyB64 = null, keyPair = null, isAuthenticated = false)
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
