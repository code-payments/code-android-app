package com.flipcash.services.user

import com.flipcash.services.models.UserFlags
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
import com.getcode.opencode.model.financial.usdf
import com.getcode.services.opencode.BuildConfig
import com.getcode.utils.TraceManager
import com.getcode.utils.base58
import com.getcode.utils.trace
import com.hoc081098.channeleventbus.ChannelEventBus
import com.mixpanel.android.mpmetrics.MixpanelAPI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authentication state machine.
 *
 * ```
 * Unknown ──→ Authenticating ──→ Ready ──→ LoggedOut
 *    │              ↓              ↑
 *    └─────→ Onboarding ──────────┘
 * ```
 *
 * - **Unknown** — initial state before credential lookup completes.
 * - **Onboarding** — account created on the server, user is mid-onboarding
 *   (access key, purchase, permissions). [ResumePoint] determines where to resume.
 * - **Authenticating** — credentials verified, loading profile/flags from storage.
 *   Transient: the app shows a loading indicator while in this state.
 * - **Ready** — fully authenticated, profile loaded, app is ready for use.
 * - **LoggedOut** — session cleared.
 */
sealed interface AuthState {
    // initial state before credential lookup completes
    data object Unknown : AuthState

    enum class ResumePoint {
        /** User created account but has not seen their access key. */
        AccessKey,
        /** User has seen their access key but must complete IAP before registration. */
        AccessKeyThenPurchase,
        /** User has seen their access key (and completed purchase if required). */
        PostAccessKey,
    }

    // account created, user is mid-onboarding (access key / purchase / permissions)
    data class Onboarding(val resumePoint: ResumePoint = ResumePoint.PostAccessKey) : AuthState

    sealed interface LoggedIn

    // credentials verified, loading profile from storage
    data object Authenticating : AuthState, LoggedIn

    // fully authenticated and ready for use
    data object Ready : AuthState, LoggedIn

    // session cleared
    data object LoggedOut : AuthState

    val canAccessAuthenticatedApis: Boolean
        get() = this is Ready

    val isAtLeastRegistered: Boolean
        get() = this is Ready || (this is Onboarding && this.resumePoint != ResumePoint.AccessKey)
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
        val previous = _state.value.authState
        _state.update { it.copy(authState = authState) }
        trace("authState change $previous => $authState")
        when (authState) {
            is AuthState.LoggedIn -> {
                accountCluster?.let { owner ->
                    eventBus.send(Events.UpdateLimits(owner = owner, force = true))
                    // Fire OnLoggedIn only on transition INTO Ready
                    if (authState is AuthState.Ready && previous !is AuthState.Ready) {
                        eventBus.send(Events.OnLoggedIn(owner))
                    }
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
            TraceManager.userId = accountId?.base58
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