package com.flipcash.app.auth

import android.annotation.SuppressLint
import androidx.core.app.NotificationManagerCompat
import com.bugsnag.android.Bugsnag
import com.flipcash.app.auth.internal.credentials.PassphraseCredentialManager
import com.flipcash.app.persistence.PersistenceProvider
import com.flipcash.app.auth.internal.extensions.token
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.controllers.ActivityFeedController
import com.flipcash.services.controllers.PushController
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.flipcash.shared.authentication.BuildConfig
import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.model.core.ID
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.google.firebase.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.messaging
import com.ionspin.kotlin.crypto.LibsodiumInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val balanceController: BalanceController,
    private val activityFeedController: ActivityFeedController,
    private val persistence: PersistenceProvider
//    private val analytics: AnalyticsService,
//    private val mixpanelAPI: MixpanelAPI
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private var softLoginDisabled: Boolean = false

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

    @SuppressLint("CheckResult")
    fun init(onInitialized: () -> Unit = { }) {
        launch {
            val token = credentialManager.lookup()
            softLogin(token.orEmpty())
                .onSuccess { LibsodiumInitializer.initializeWithCallback(onInitialized) }
                .onFailure(ErrorUtils::handleError)
        }
    }

    private suspend fun softLogin(entropyB64: String): Result<ID> {
        if (softLoginDisabled) return Result.failure(Throwable("Disabled"))
        return login(entropyB64, isSoftLogin = true)
    }

    suspend fun createAccount(): Result<Unit> {
        return credentialManager.create()
            .onSuccess { account ->
                persistence.openDatabase(account.entropy)
                // TODO: this will move to post IAP
                userManager.accountCluster?.let { balanceController.onUserLoggedIn(it) }

                accountController.getUserFlags()
            }.onFailure {
                userManager.clear()
            }.map { Unit }
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

        if (!isSoftLogin) {
            softLoginDisabled = true
            loginAnalytics()
        }

        return credentialManager.login(entropyB64)
            .onSuccess { account ->
                persistence.openDatabase(entropyB64)
                // TODO: this will move to post IAP check
                userManager.accountCluster?.let { balanceController.onUserLoggedIn(it) }

                userManager.set(userId = account.id)

                accountController.getUserFlags()
                    .onSuccess { flags ->
                        userManager.set(flags)
                    }.onFailure {
                        taggedTrace("Failed to get user flags", type = TraceType.Error, cause = it)
                        userManager.set(authState = AuthState.Unregistered)
                    }

                savePrefs()
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

    suspend fun logout(): Result<Unit> {
        return credentialManager.logout()
            .onSuccess { resetStateForUser() }
    }

    private fun loginAnalytics() {
        taggedTrace("analytics login event")
//        analytics.login(
//            ownerPublicKey = owner.getPublicKeyBase58(),
//            autoCompleteCount = 0,
//            inputChangeCount = 0
//        )
    }

    private suspend fun resetStateForUser() {
        FirebaseMessaging.getInstance().deleteToken()
        pushController.deleteTokens()
        notificationManager.cancelAll()
        userManager.clear()
        balanceController.reset()
        persistence.close()
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
