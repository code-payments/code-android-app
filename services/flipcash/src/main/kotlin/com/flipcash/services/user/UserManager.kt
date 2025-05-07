package com.flipcash.services.user

import com.bugsnag.android.Bugsnag
import com.flipcash.services.internal.model.account.UserFlags
import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivePath.Companion
import com.getcode.crypt.DerivedKey
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.events.Events
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.uuid
import com.getcode.services.opencode.BuildConfig
import com.hoc081098.channeleventbus.ChannelEventBus
import com.mixpanel.android.mpmetrics.MixpanelAPI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthState {
    // still to determine
    data object Unknown : AuthState
    // account has been created but not yet paid for
    // seenAccessKey used as a flag whether to land them back on
    // access key screen or purchase
    data class Unregistered(val seenAccessKey: Boolean = true) : AuthState
    // account has been created and paid for
    // and we are waiting for metadata to be pulled from storage
    data object LoggedInAwaitingUser : AuthState
    // account is paid for and we ready for use in app
    data object LoggedIn : AuthState
    // logged out
    data object LoggedOut : AuthState

    val canAccessAuthenticatedApis: Boolean
        get() = this is LoggedIn
}

@Singleton
class UserManager @Inject constructor(
    private val mnemonicManager: MnemonicManager,
    private val mixpanelAPI: MixpanelAPI,
    private val eventBus: ChannelEventBus,
    balanceController: BalanceController,
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

    val userFlags: UserFlags?
        get() = _state.value.flags

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

    init {
        balanceController.onTimelockUnlocked = {
            didDetectUnlockedAccount()
        }
    }

    fun establish(entropy: String) {
        val mnemonic = mnemonicManager.fromEntropyBase64(entropy)
        val authority = DerivedKey.derive(DerivePath.primary, mnemonic)
        val cluster = AccountCluster.newInstance(authority)
        _state.update {
            it.copy(
                entropy = entropy,
                cluster = cluster,
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

        when (authState) {
            AuthState.LoggedIn,
            AuthState.LoggedInAwaitingUser -> {
                accountCluster?.let { owner ->
                    eventBus.send(Events.UpdateLimits(owner = owner, force = true))
                }
            }
            else -> Unit
        }
    }

    fun set(userFlags: UserFlags?) {
        _state.update {
            it.copy(
                flags = userFlags,
            )
        }

//        set(if (userFlags?.isRegistered == true) AuthState.LoggedIn else AuthState.Unregistered)

        if (userFlags?.isRegistered == true) {
            accountCluster?.let { eventBus.send(Events.OnLoggedIn(accountCluster!!)) }
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