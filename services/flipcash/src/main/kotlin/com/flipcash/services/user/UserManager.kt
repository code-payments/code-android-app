package com.flipcash.services.user

import com.bugsnag.android.Bugsnag
import com.flipcash.services.internal.model.account.UserFlags
import com.getcode.crypt.DerivedKey
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.uuid
import com.getcode.services.opencode.BuildConfig
import com.mixpanel.android.mpmetrics.MixpanelAPI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthState {
    data object Unknown : AuthState
    data object Unregistered : AuthState
    data object LoggedInAwaitingUser : AuthState
    data object LoggedIn : AuthState
    data object LoggedOut : AuthState
}

@Singleton
class UserManager @Inject constructor(
    private val mnemonicManager: MnemonicManager,
    private val mixpanelAPI: MixpanelAPI,
) {
    private val _state: MutableStateFlow<State> = MutableStateFlow(State())
    val state: StateFlow<State>
        get() = _state.asStateFlow()

    val entropy: String?
        get() = _state.value.entropy

    val accountCluster: AccountCluster?
        get() = _state.value.cluster

    val userId: ID?
        get() = _state.value.userId

    val authState: AuthState
        get() = _state.value.authState

    data class State(
        val authState: AuthState = AuthState.Unknown,
        val entropy: String? = null,
        val cluster: AccountCluster? = null,
        val userId: ID? = null,
        val flags: UserFlags? = null,
        val isTimelockUnlocked: Boolean = false,
    )

    fun establish(entropy: String) {
        val mnemonic = mnemonicManager.fromEntropyBase64(entropy)
        val authority = DerivedKey.derive(com.getcode.crypt.DerivePath.primary, mnemonic)
        _state.update {
            it.copy(
                entropy = entropy,
                cluster = AccountCluster.newInstance(authority),
            )
        }
    }

    fun set(userId: ID) {
        _state.update {
            it.copy(userId = userId)
        }
        associate()
    }

    fun set(authState: AuthState) {
        _state.update { it.copy(authState = authState) }
    }

    fun set(userFlags: UserFlags?) {
        _state.update {
            it.copy(
                flags = userFlags,
                authState = if (userFlags?.isRegistered == true) AuthState.LoggedIn else AuthState.Unregistered
            )
        }
        associate()
    }

    fun didDetectUnlockedAccount() {
        _state.update {
            if (!it.isTimelockUnlocked) {
                it.copy(isTimelockUnlocked = true)
            } else {
                it
            }
        }
    }

    fun isSelf(id: ID?) = userId == id

    private fun associate() {
        if (!BuildConfig.DEBUG) {
            val distinctId = userId?.uuid?.toString()
            if (Bugsnag.isStarted()) {
                Bugsnag.setUser(distinctId, null, "")
            }

            mixpanelAPI.identify(distinctId)
        }
    }

    fun clear() {
        _state.update {
            it.copy(
                authState = AuthState.LoggedOut,
                entropy = null,
                flags = null,
                cluster = null,
                userId = emptyList(),
                isTimelockUnlocked = false,
            )
        }
    }
}