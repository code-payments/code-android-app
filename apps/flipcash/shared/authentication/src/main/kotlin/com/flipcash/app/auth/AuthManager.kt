package com.flipcash.app.auth

import androidx.core.app.NotificationManagerCompat
import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.auth.internal.credentials.LookupResult
import com.flipcash.app.auth.internal.credentials.PassphraseCredentialManager
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.persistence.PersistenceProvider
import com.flipcash.app.push.PushTokenProvider
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.shared.profile.ProfileCoordinator
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.controllers.PushController
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.flipcash.shared.authentication.BuildConfig
import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.model.core.ID
import com.getcode.utils.TraceManager
import com.getcode.utils.TraceType
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.network.retryable
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    private val credentialManager: PassphraseCredentialManager,
    private val userManager: UserManager,
    private val notificationManager: NotificationManagerCompat,
    private val accountController: AccountController,
    private val ocpAccountController: com.getcode.opencode.controllers.AccountController,
    private val profileController: ProfileController,
    private val pushController: PushController,
    private val pushTokenProvider: PushTokenProvider,
    private val tokenCoordinator: TokenCoordinator,
    private val persistence: PersistenceProvider,
    private val featureFlags: FeatureFlagController,
    private val appSettings: AppSettingsCoordinator,
    private val userFlags: UserFlagsCoordinator,
    private val profileCoordinator: ProfileCoordinator,
    private val contactCoordinator: ContactCoordinator,
    private val networkObserver: NetworkConnectivityListener,
    private val dispatchers: DispatcherProvider,
//    private val analytics: AnalyticsService,
) : CoroutineScope by CoroutineScope(dispatchers.IO) {

    init {
        // Persist onboarding completion when the composable transitions Onboarding → Ready.
        var previous: AuthState = AuthState.Unknown
        userManager.state
            .map { it.authState }
            .distinctUntilChanged()
            .onEach { current ->
                if (previous is AuthState.Onboarding && current is AuthState.Ready) {
                    launch { credentialManager.markOnboardingCompleted() }
                }
                previous = current
            }
            .launchIn(this)
    }

    private var softLoginDisabled: Boolean = false

    /**
     * Serializes [init] so concurrent callers (app startup and the FCM push
     * handler both call it when no account is established) can't launch two
     * soft-logins at once — a second login re-inits the per-user database and
     * would close the connection pool out from under active Room queries.
     */
    private val initMutex = Mutex()

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
            initMutex.withLock {
                // A concurrent init already logged us in (e.g. app startup won the
                // race against an FCM push). Don't run a second soft-login that
                // would re-init the database and close its connection pool mid-query.
                if (userManager.authState is AuthState.LoggedIn) {
                    onInitialized()
                    return@withLock
                }

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
                        userManager.set(AuthState.Onboarding(result.resumePoint))
                    }
                }
            }
        }
    }

    private suspend fun softLogin(entropyB64: String): Result<ID> {
        if (softLoginDisabled) return Result.failure(Throwable("Disabled"))
        return login(entropyB64, isSoftLogin = true)
    }

    /**
     * Provisions the core OCP USDF account and awaits the result. Onboarding calls
     * this at the access-key step to gate release to the scanner. Returns
     * [Result.failure] (e.g. antispam denial) when the caller must not proceed.
     */
    suspend fun ensureCoreAccountProvisioned(): Result<Unit> {
        val owner = userManager.accountCluster
            ?: return Result.failure(
                IllegalStateException("Cannot provision core account: no account cluster established")
            )
        return ocpAccountController.ensureCoreAccount(owner)
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
                    val resumePoint = if (userManager.userFlags?.requiresIapForRegistration == true) {
                        AuthState.ResumePoint.AccessKeyThenPurchase
                    } else {
                        AuthState.ResumePoint.PostAccessKey
                    }
                    userManager.set(AuthState.Onboarding(resumePoint))
                }
            }.map { Unit }
    }

    suspend fun presentCredentialStorage(): Result<Unit> {
        return credentialManager.presentSaveOption()
            .onSuccess {
                accountController.getUserFlags().onSuccess { flags ->
                    userManager.set(flags)
                }
            }.map { Unit }
    }

    suspend fun onAccountPurchased(): Result<Unit> {
        return credentialManager.onAccountPurchased()
            .fold(
                onSuccess = {
                    val flagsResult = accountController.getUserFlags()
                        .onSuccess { userManager.set(it) }
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
                        profileCoordinator.restore()
                        userFlags.restoreFlags()

                        val flags = if (!isSoftLogin || networkObserver.isConnected) {
                            retryable(maxRetries = 3) {
                                accountController.getUserFlags().getOrNull()
                            }
                        } else {
                            null
                        }

                        val seenAccessKey = credentialManager.hasSeenAccessKey()
                        // Interactive logins (seed input, deep link, credential picker)
                        // always route through the onboarding permissions flow, which
                        // sets Ready on completion. Don't trust the completedOnboarding
                        // default (true for backward compat) here — it would skip the
                        // permissions phase and set Ready too early.
                        // Soft logins (app restart) can trust the persisted flag.
                        val completedOnboarding = if (!isSoftLogin) false
                            else credentialManager.hasCompletedOnboarding()
                        if (flags != null) {
                            userManager.set(flags)
                            if (flags.isRegistered && seenAccessKey && completedOnboarding) {
                                userManager.set(AuthState.Ready)
                            } else {
                                val resumePoint = when {
                                    !seenAccessKey -> AuthState.ResumePoint.AccessKey
                                    flags.requiresIapForRegistration -> AuthState.ResumePoint.AccessKeyThenPurchase
                                    else -> AuthState.ResumePoint.PostAccessKey
                                }
                                userManager.set(AuthState.Onboarding(resumePoint))
                            }
                        } else {
                            taggedTrace("Failed to get user flags after retries", type = TraceType.Error)
                            if (seenAccessKey && completedOnboarding) {
                                userManager.set(authState = AuthState.Ready)
                            } else {
                                val resumePoint = if (seenAccessKey) AuthState.ResumePoint.PostAccessKey
                                    else AuthState.ResumePoint.AccessKey
                                userManager.set(authState = AuthState.Onboarding(resumePoint))
                            }
                        }

                        if (networkObserver.isConnected) {
                            profileController.updateUserProfile()
                        }
                    }
                    if (networkObserver.isConnected) {
                        launch { savePrefs() }
                    }
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
        // Wipe server contact set before logout while the session can still authenticate.
        contactCoordinator.clearServerContactSet()
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
        // Intentionally do NOT close the Room DB here. Closing it while the just-
        // dismissed Cash screen's stateIn(WhileSubscribed(5000)) flows are still
        // active tears the connection pool out from under an in-flight Room query
        // -> "SQLException: connection is closed" (SQLITE_MISUSE). The DB lifecycle
        // is owned by FlipcashDatabase.init(): a same-user re-login reuses the open
        // instance, and a different user's login rebuilds it for their entropy.
        featureFlags.reset()
        appSettings.reset()
        userFlags.clearAll()
        profileCoordinator.reset()
        userFlags.resetCache()

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
