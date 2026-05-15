package com.flipcash.app.auth

import androidx.core.app.NotificationManagerCompat
import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.auth.internal.credentials.LookupResult
import com.flipcash.app.auth.internal.credentials.PassphraseCredentialManager
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.persistence.PersistenceProvider
import com.flipcash.app.push.PushTokenProvider
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.controllers.PushController
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.flipcash.shared.authentication.BuildConfig
import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.model.core.ID
import com.getcode.utils.TraceManager
import com.getcode.utils.TraceType
import com.getcode.utils.network.retryable
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    private val credentialManager: PassphraseCredentialManager,
    private val userManager: UserManager,
    private val notificationManager: NotificationManagerCompat,
    private val accountController: AccountController,
    private val pushController: PushController,
    private val pushTokenProvider: PushTokenProvider,
    private val tokenCoordinator: TokenCoordinator,
    private val persistence: PersistenceProvider,
    private val featureFlagController: FeatureFlagController,
    private val appSettings: AppSettingsCoordinator,
    private val userFlags: UserFlagsCoordinator,
//    private val analytics: AnalyticsService,
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private var softLoginDisabled: Boolean = false

    /**
     * Entropy for the account being switched to. Set before logout so App.kt's
     * auth guard can navigate to Login(entropy) instead of seedless Login().
     */
    var pendingSwitchEntropy: String? = null
        private set

    companion object {
        private const val TAG = "AuthManager"
        internal fun taggedTrace(
            message: String,
            type: TraceType = TraceType.Log,
            cause: Throwable? = null
        ) {
            trace(message = message, type = type, tag = TAG, error = cause)
        }
    }

    fun init(onInitialized: () -> Unit = { }) {
        launch {
            when (val result = credentialManager.lookup()
                .also { taggedTrace("lookup result: ${it::class.simpleName}") }) {
                is LookupResult.ExistingAccountFound -> {
                    val token = result.entropy
                    softLogin(token)
                        .onSuccess { onInitialized() }
                }

                LookupResult.NoAccountFound -> Unit
                is LookupResult.TemporaryAccountCreated -> {
                    userManager.establish(entropy = result.entropy)
                    userManager.set(AuthState.Registered(result.seenAccessKey))
                }
            }
        }
    }

    private suspend fun softLogin(entropyB64: String): Result<ID> {
        if (softLoginDisabled) return Result.failure(Throwable("Disabled"))
        return login(entropyB64, isSoftLogin = true)
    }

    suspend fun createAccount(): Result<Unit> {
        return credentialManager.createAccount()
            .fold(
                onSuccess = { entropy ->
                    accountController.getUserFlags()
                        .onSuccess {
                            userManager.set(it)
                            if (!it.requiresIapForRegistration) {
                                onAccountPurchased()
                            }
                        }
                        .map { entropy }
                },
                onFailure = { Result.failure(it) }
            ).onSuccess { entropy ->
                persistence.openDatabase(entropy)
            }.map { Unit }
    }

    suspend fun onUserAccessKeySeen(): Result<Unit> {
        return credentialManager.onUserAccessKeySeen()
            .onSuccess {
                if (userManager.authState !is AuthState.LoggedIn) {
                    userManager.set(AuthState.Registered(true))
                }
            }.map { Unit }
    }

    suspend fun presentCredentialStorage(): Result<Unit> {
        return credentialManager.presentSaveOption()
            .onSuccess {
                accountController.getUserFlags().onSuccess { flags ->
                    userManager.set(flags)
                    if (flags.isRegistered) {
                        userManager.set(AuthState.LoggedInWithUser)
                    }
                }
            }.map { Unit }
    }

    suspend fun onAccountPurchased(): Result<Unit> {
        return credentialManager.onAccountPurchased()
            .fold(
                onSuccess = {
                    val flagsResult = accountController.getUserFlags()
                        .onSuccess { userManager.set(it) }
                    userManager.set(AuthState.LoggedInWithUser)
                    flagsResult
                },
                onFailure = { Result.failure(it) }
            ).onSuccess { savePrefs() }.map { Unit }
    }

    suspend fun login(
        entropyB64: String,
        isSoftLogin: Boolean = false,
        isFromSelection: Boolean = false,
        rollbackOnError: Boolean = false
    ): Result<ID> {
        taggedTrace("Login: isSoftLogin: $isSoftLogin, rollbackOnError: $rollbackOnError")

        if (entropyB64.isEmpty()) {
            userManager.clear()
            return Result.failure(Throwable("Provided entropy was empty"))
        }

        if (!isSoftLogin) {
            softLoginDisabled = true
            loginAnalytics()
        }

        return credentialManager.login(entropyB64, isFromSelection)
            .onSuccess { account ->
                persistence.openDatabase(entropyB64)
                userManager.establish(entropyB64)
                userManager.set(accountId = account.id)

                coroutineScope {
                    launch {
                        val flags = retryable(maxRetries = 3) {
                            accountController.getUserFlags().getOrNull()
                        }

                        if (flags != null) {
                            userManager.set(flags)
                            userManager.set(if (flags.isRegistered) AuthState.LoggedInWithUser else AuthState.Registered())
                        } else {
                            taggedTrace("Failed to get user flags after retries", type = TraceType.Error)
                            userManager.set(authState = AuthState.Registered())
                        }
                    }
                    launch { savePrefs() }
                }
            }.onFailure {
                logout()
                resetStateForUser()
            }.map { it.id }
    }


    suspend fun selectAccount(): Result<MnemonicPhrase> {
        return credentialManager.selectCredential()
    }

    suspend fun deleteAndLogout(): Result<Unit> {
        //todo: add account deletion
        return logout()
    }

    suspend fun logoutAndSwitchAccount(entropy: String): Result<String> {
        pendingSwitchEntropy = entropy
        return logout().map { entropy }
    }

    fun consumePendingSwitchEntropy(): String? {
        return pendingSwitchEntropy.also { pendingSwitchEntropy = null }
    }

    suspend fun logout(): Result<Unit> {
        return credentialManager.logout()
            .onSuccess { resetStateForUser() }
    }

    private fun loginAnalytics() {
//        analytics.login(
//            ownerPublicKey = owner.getPublicKeyBase58(),
//            autoCompleteCount = 0,
//            inputChangeCount = 0
//        )
    }

    private suspend fun resetStateForUser() {
        // Fire-and-forget slow network operations to avoid blocking navigation
        launch {
            pushTokenProvider.deleteToken()
            pushController.deleteTokens()
        }
        notificationManager.cancelAll()
        userManager.clear()
        tokenCoordinator.reset()
        persistence.close()
        featureFlagController.reset()
        appSettings.reset()
        userFlags.clearAll()

        if (!BuildConfig.DEBUG) TraceManager.userId = null
    }

    private suspend fun savePrefs() {
        updateFcmToken()
    }

    private suspend fun updateFcmToken() {
        val pushToken = pushTokenProvider.getToken() ?: return
        pushController.addToken(pushToken)
            .onSuccess {
                userManager.set(pushToken = pushToken)
                trace("push token updated", type = TraceType.Silent)
            }.onFailure {
                trace(message = "Failure updating push token", error = it)
            }
    }

    sealed class AuthManagerException : Exception() {
        class TimelockUnlockedException : AuthManagerException()
    }
}
