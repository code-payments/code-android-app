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

    /**
     * Reset account-scoped state on logout, preserving device-scoped fields that are
     * derived purely from feature flags via `observe(flag)`.
     *
     * Those flag observers are hot [StateFlow]s that only re-emit on a *value change*.
     * A blanket `SessionState()` reset would clobber these fields to their defaults, and
     * the observer would not re-push its unchanged current value to repopulate them —
     * leaving the UI desynced from the still-persisted flag (e.g. Tipping flag on, but the
     * scanner Tips tab gone). Account/token/settings-derived fields are safe to reset:
     * their sources re-emit when the account changes, so they self-heal.
     */
    fun reset() {
        _state.update { prev ->
            SessionState(
                vibrateOnScan = prev.vibrateOnScan,
                showNetworkOffline = prev.showNetworkOffline,
                isTippingEnabled = prev.isTippingEnabled,
            )
        }
    }
}
