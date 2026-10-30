package com.flipcash.app.devicelink

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The phone's link flow as state: scan, compare codes, send. [seed] supplies the signed-in
 * account's base64 entropy and is read only after the user confirms the codes match.
 */
class PhoneLinkController(
    private val scope: CoroutineScope,
    private val seed: suspend () -> String?,
) {
    sealed interface State {
        data object Scanning : State
        data object Connecting : State
        data class Confirm(val code: String) : State
        data object Sending : State
        data object Linked : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Scanning)
    val state: StateFlow<State> = _state.asStateFlow()
    private var pending: PhoneLinker.Pending? = null

    fun onScanned(text: String) {
        if (_state.value != State.Scanning) return
        val invite = LinkInvite.parse(text) ?: return
        _state.value = State.Connecting
        scope.launch {
            PhoneLinker.connect(invite)
                .onSuccess {
                    pending = it
                    _state.value = State.Confirm(it.code)
                }
                .onFailure { _state.value = State.Failed(it.message ?: "Couldn't reach the desktop") }
        }
    }

    fun confirm() {
        val p = pending ?: return
        pending = null
        _state.value = State.Sending
        scope.launch {
            val entropy = seed()
            if (entropy == null) {
                p.cancel()
                _state.value = State.Failed("This phone isn't signed in")
                return@launch
            }
            p.confirm(entropy)
                .onSuccess { _state.value = State.Linked }
                .onFailure { _state.value = State.Failed(it.message ?: "The link failed") }
        }
    }

    fun cancel() {
        val p = pending
        pending = null
        scope.launch { p?.cancel() }
        _state.value = State.Scanning
    }
}
