package com.flipcash.services.user

import com.bugsnag.android.Bugsnag
import com.flipcash.services.internal.model.account.UserFlags
import com.flipcash.services.models.UserProfile
import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.events.Events
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.PoolAccount
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.NoId
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdc
import com.getcode.opencode.model.financial.usdf
import com.getcode.services.opencode.BuildConfig
import com.getcode.utils.base58
import com.getcode.utils.trace
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
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

    // account has been created but not yet paid for (if required)
    // seenAccessKey used as a flag whether to land them back on
    // access key screen or purchase
    data class Registered(val seenAccessKey: Boolean = true) : AuthState

    sealed interface LoggedIn

    // account has been created and paid for (if required)
    // and we are waiting for metadata to be pulled from storage
    data object LoggedInAwaitingUser : AuthState, LoggedIn

    // account is paid for (if required) and is ready for use in app
    data object LoggedInWithUser : AuthState, LoggedIn

    // logged out
    data object LoggedOut : AuthState

    val canAccessAuthenticatedApis: Boolean
        get() = this is LoggedInWithUser

    val isAtLeastRegistered: Boolean
        get() = this is LoggedInWithUser || this is Registered
}

@Singleton
class UserManager @Inject constructor(
    private val mnemonicManager: MnemonicManager,
    private val mixpanelAPI: MixpanelAPI,
    private val eventBus: ChannelEventBus,
    accountController: AccountController,
) {
    private val _state: MutableStateFlow<State> = MutableStateFlow(State())
    val state: StateFlow<State>
        get() = _state.asStateFlow()

    val entropy: String?
        get() = _state.value.entropy

    val mnemnonic: MnemonicPhrase?
        get() = entropy?.let { MnemonicPhrase.fromEntropyB64(it) }

    val accountCluster: AccountCluster?
        get() = _state.value.cluster

    val accountId: ID?
        get() = _state.value.accountId

    val userFlags: UserFlags?
        get() = _state.value.flags

    val authState: AuthState
        get() = _state.value.authState

    val profile: UserProfile?
        get() = _state.value.userProfile

    data class State(
        val authState: AuthState = AuthState.Unknown,
        val entropy: String? = null,
        val cluster: AccountCluster? = null,
        val accountId: ID? = null,
        val flags: UserFlags? = null,
        val isTimelockUnlocked: Boolean = false,
        val pushToken: String? = null,
        val userProfile: UserProfile? = null,
    )

    init {
        accountController.onTimelockUnlocked = {
            didDetectUnlockedAccount()
        }

        Firebase.messaging.token.addOnSuccessListener { token ->
            set(pushToken = token)
        }
    }

    fun establish(entropy: String) {
        val mnemonic = mnemonicManager.fromEntropyBase64(entropy)
        val authority = DerivedKey.derive(DerivePath.primary, mnemonic)
        val cluster = AccountCluster.newInstance(authority = authority, token = Token.usdf)
        _state.update {
            it.copy(
                entropy = entropy,
                cluster = cluster,
            )
        }

        associate()
    }

    fun set(accountId: ID) {
        _state.update {
            it.copy(accountId = accountId)
        }
    }

    fun set(authState: AuthState) {
        _state.update { it.copy(authState = authState) }

        when (authState) {
            is AuthState.LoggedIn -> {
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

        if (userFlags?.isRegistered == true) {
            accountCluster?.let { eventBus.send(Events.OnLoggedIn(accountCluster!!)) }
        }
    }

    fun set(pushToken: String?) {
        _state.update { it.copy(pushToken = pushToken) }
    }

    internal fun set(userProfile: UserProfile) {
        _state.update { it.copy(userProfile = userProfile) }
    }

    fun poolAccountAt(index: Long): PoolAccount {
        return PoolAccount.create(
            mnemonic = mnemnonic!!,
            index = index
        )
    }

    private fun didDetectUnlockedAccount() {
        _state.update {
            if (!it.isTimelockUnlocked) {
                it.copy(isTimelockUnlocked = true)
            } else {
                it
            }
        }
    }

    private fun associate() {
        if (!BuildConfig.DEBUG) {
            if (Bugsnag.isStarted()) {
                Bugsnag.setUser(accountId?.base58, null, "")
            }

            mixpanelAPI.identify(accountId?.base58)
        }
    }

    fun clear() {
        _state.update {
            it.copy(
                authState = AuthState.LoggedOut,
                entropy = null,
                flags = null,
                cluster = null,
                accountId = NoId,
                isTimelockUnlocked = false,
                userProfile = null,
            )
        }
    }
}