package com.flipcash.app.session.internal

import com.flipcash.app.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe holder for the current [SessionState].
 *
 * Injected into every session delegate and the [RealSessionController] shell so they
 * share a single source of truth for session-wide state (auth flags, camera state,
 * feature flags, balances, etc.) without any delegate holding the raw
 * `MutableStateFlow`.
 */
@Singleton
class SessionStateHolder @Inject constructor() {
    private val _state = MutableStateFlow(SessionState())

    /** Observable session state, collected by `RealSessionController.state`. */
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Snapshot of the current state — useful in non-suspending contexts. */
    val current: SessionState get() = _state.value

    /** Atomically update the state via a transform function. */
    fun update(transform: (SessionState) -> SessionState) { _state.update(transform) }

    /** Reset to the default [SessionState] (e.g. on logout). */
    fun reset() { _state.value = SessionState() }
}
