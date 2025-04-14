package com.flipcash.app.core.auth

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.bugsnag.android.Bugsnag
import com.flipcash.app.core.AccountType
import com.flipcash.app.core.internal.accounts.AccountUtils
import com.flipcash.app.core.internal.accounts.UserIdResult
import com.flipcash.app.core.internal.extensions.token
import com.flipcash.core.BuildConfig
import com.flipcash.services.FlipcashCore
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.controllers.PushController
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.repositories.EventRepository
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.base58
import com.getcode.utils.encodeBase64
import com.getcode.utils.trace
import com.getcode.vendor.Base58
import com.google.firebase.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.messaging
import com.ionspin.kotlin.crypto.LibsodiumInitializer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userManager: UserManager,
    private val notificationManager: NotificationManagerCompat,
    @AccountType
    private val accountType: String,
    private val accountController: AccountController,
    private val pushController: PushController,
    private val balanceController: BalanceController,
    private val eventRepository: EventRepository,
//    private val analytics: AnalyticsService,
//    private val mixpanelAPI: MixpanelAPI
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private var softLoginDisabled: Boolean = false

    companion object {
        private const val TAG = "AuthManager"
        internal fun taggedTrace(message: String, type: TraceType = TraceType.Log, cause: Throwable? = null) {
            trace(message = message, type = type, tag = TAG, error = cause)
        }
    }

    @SuppressLint("CheckResult")
    fun init(onInitialized: () -> Unit = { }) {
        launch {
            val token = AccountUtils.getToken(context, accountType)?.token
            softLogin(token.orEmpty())
                .onSuccess { LibsodiumInitializer.initializeWithCallback(onInitialized) }
                .onFailure(ErrorUtils::handleError)
        }
    }

    private suspend fun softLogin(entropyB64: String): Result<ID> {
        if (softLoginDisabled) return Result.failure(Throwable("Disabled"))
        return login(entropyB64, isSoftLogin = true)
    }

    private fun setupAsNew(): String {
        val seedB64 = Ed25519.createSeed16().encodeBase64()
        userManager.establish(seedB64)
        return seedB64
    }

    suspend fun createAccount(): Result<ID> {
        val entropy = setupAsNew()
        FlipcashCore.initialize(context, entropy)

        return accountController.createAccount()
            .onSuccess { userId ->
                AccountUtils.addAccount(
                    context = context,
                    name = "Flipcash User",
                    password = userId.base58,
                    token = entropy,
                    type = accountType,
                    isUnregistered = true
                )
                userManager.set(userId)
                userManager.set(AuthState.Unregistered)

                // TODO: this will move to post IAP
                userManager.accountCluster?.let { balanceController.onUserLoggedIn(it) }

                accountController.getUserFlags()
            }.onFailure {
                userManager.clear()
//                clearToken()
            }
    }

    suspend fun login(
        entropyB64: String,
        isSoftLogin: Boolean = false,
        rollbackOnError: Boolean = false
    ): Result<ID> {
        taggedTrace("Login: isSoftLogin: $isSoftLogin, rollbackOnError: $rollbackOnError")

        if (entropyB64.isEmpty()) {
            userManager.clear()
            return Result.failure(Throwable("Provided entropy was empty"))
        }

        FlipcashCore.initialize(context, entropyB64)

        userManager.establish(entropy = entropyB64)
        userManager.set(AuthState.LoggedInAwaitingUser)

        if (!isSoftLogin) {
            softLoginDisabled = true
            loginAnalytics()
        }

        val ret = if (isSoftLogin) {
            val lookup = AccountUtils.getUserId(context, accountType)
            taggedTrace("Login userId lookup => $lookup")
            when (lookup) {
                is UserIdResult.Registered -> Result.success(Base58.decode(lookup.userId).toList())
                is UserIdResult.Unregistered -> Result.success(Base58.decode(lookup.userId).toList())
                null -> Result.failure(Throwable("No user Id found"))
            }
        } else {
            accountController.login()
        }

        return ret
            .onSuccess { userId ->
                if (!isSoftLogin) {
                    AccountUtils.addAccount(
                        context = context,
                        name = "Flipcash User", // TODO: ?
                        password = userId.base58,
                        token = entropyB64,
                        type = accountType,
                        isUnregistered = false,
                    )
                }
                // TODO: this will move to post IAP check
                userManager.accountCluster?.let { balanceController.onUserLoggedIn(it) }

                userManager.set(userId = userId)

                accountController.getUserFlags()
                    .onSuccess { flags ->
                        userManager.set(flags)
                    }.onFailure {
                        taggedTrace("Failed to get user flags", type = TraceType.Error, cause = it)
                        userManager.set(authState =  AuthState.Unregistered)
                    }

                savePrefs()
            }
            .onFailure {
                logout(context)
                clearToken()
            }
    }

    suspend fun deleteAndLogout(context: Context): Result<Unit> {
        //todo: add account deletion
        return logout(context)
    }

    suspend fun logout(context: Context): Result<Unit> {
        return AccountUtils.removeAccounts(context, accountType).toFlowable()
            .to { runCatching { it.firstOrError().blockingGet() } }
            .map { clearToken() }
            .map { Result.success(Unit) }
    }

    private fun loginAnalytics() {
        taggedTrace("analytics login event")
//        analytics.login(
//            ownerPublicKey = owner.getPublicKeyBase58(),
//            autoCompleteCount = 0,
//            inputChangeCount = 0
//        )
    }

    private suspend fun clearToken() {
        FirebaseMessaging.getInstance().deleteToken()
        pushController.deleteTokens()
        notificationManager.cancelAll()
        FlipcashCore.reset(context)
        userManager.clear()
        balanceController.reset()
        if (!BuildConfig.DEBUG) Bugsnag.setUser(null, null, null)
    }

    private suspend fun savePrefs() {
        updateFcmToken()
    }

    @SuppressLint("CheckResult")
    private suspend fun updateFcmToken() {
        val pushToken = Firebase.messaging.token() ?: return
        pushController.addToken(pushToken)
            .onSuccess {
                trace("push token updated", type = TraceType.Silent)
            }.onFailure {
                trace(message = "Failure updating push token", error = it)
            }
    }

    sealed class AuthManagerException : Exception() {
        class TimelockUnlockedException : AuthManagerException()
    }
}
