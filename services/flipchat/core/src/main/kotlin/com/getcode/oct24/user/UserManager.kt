package com.getcode.oct24.user

import com.getcode.crypt.DerivedKey
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.generator.OrganizerGenerator
import com.getcode.model.ID
import com.getcode.services.manager.MnemonicManager
import com.getcode.solana.organizer.Organizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager @Inject constructor(
    private val mnemonicManager: MnemonicManager,
    private val organizerGenerator: OrganizerGenerator,
) {
    private val _state: MutableStateFlow<State> = MutableStateFlow(State())
    val state: StateFlow<State>
        get() = _state.asStateFlow()

    val entropy: String?
        get() = _state.value.entropy

    val keyPair: KeyPair?
        get() = _state.value.keyPair

    val userId: ID?
        get() = _state.value.userId

    val organizer: Organizer?
        get() = _state.value.organizer

    val displayName: String?
        get() = _state.value.displayName

    data class State(
        val entropy: String? = null,
        val keyPair: KeyPair? = null,
        val userId: ID? = null,
        val displayName: String? = null,
        val organizer: Organizer? = null,
    )

    fun establish(entropy: String) {
        val mnemonic = mnemonicManager.fromEntropyBase64(entropy)
        val authority = DerivedKey.derive(com.getcode.crypt.DerivePath.primary, mnemonic)
        val organizer = organizerGenerator.generate(mnemonic)
        _state.update {
            it.copy(entropy = entropy, keyPair = authority.keyPair, organizer = organizer)
        }
    }

    fun set(userId: ID) {
        _state.update {
            it.copy(userId = userId)
        }
    }

    fun set(displayName: String) {
        _state.update {
            it.copy(displayName = displayName)
        }
    }

    fun set(organizer: Organizer) {
        _state.update {
            it.copy(organizer = organizer)
        }
    }

    fun clear() {
        _state.update {
            it.copy(entropy = null, keyPair = null, userId = emptyList(), organizer = null)
        }
    }
}