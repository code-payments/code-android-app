package com.getcode.oct24.user

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager @Inject constructor() {
    private val _state: MutableStateFlow<State> = MutableStateFlow(State())
    val state: StateFlow<State>
        get() = _state.asStateFlow()

    val keyPair: KeyPair?
        get() = _state.value.keyPair

    val userId: ID?
        get() = _state.value.userId

    data class State(
        val keyPair: KeyPair? = null,
        val userId: ID? = null,
    )

    fun set(keyPair: KeyPair) {
        _state.update {
            it.copy(keyPair = keyPair)
        }
    }

    fun set(userId: ID) {
        _state.update {
            it.copy(userId = userId)
        }
    }

    fun set(keyPair: KeyPair, userId: ID) {
        _state.update {
            it.copy(keyPair = keyPair, userId = userId)
        }
    }

    fun clear() {
        _state.update {
            it.copy(keyPair = null, userId = null)
        }
    }
}