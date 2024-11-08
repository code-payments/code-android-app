package com.getcode.oct24.auth

import android.annotation.SuppressLint
import android.content.Context
import com.bugsnag.android.Bugsnag
import com.getcode.analytics.AnalyticsService
import com.getcode.db.CodeAppDatabase
import com.getcode.db.InMemoryDao
import com.getcode.ed25519.Ed25519
import com.getcode.manager.SessionManager
import com.getcode.model.ID
import com.getcode.network.BalanceController
import com.getcode.network.NotificationCollectionHistoryController
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.isMock
import com.getcode.oct24.BuildConfig
import com.getcode.oct24.FlipchatServices
import com.getcode.oct24.network.controllers.AuthController
import com.getcode.oct24.network.controllers.ProfileController
import com.getcode.oct24.user.UserManager
import com.getcode.oct24.util.AccountUtils
import com.getcode.oct24.util.TokenResult
import com.getcode.services.db.Database
import com.getcode.services.utils.installationId
import com.getcode.services.utils.token
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.base58
import com.getcode.utils.encodeBase64
import com.getcode.utils.getPublicKeyBase58
import com.getcode.utils.trace
import com.google.firebase.Firebase
import com.google.firebase.installations.installations
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.messaging
import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.mixpanel.android.mpmetrics.MixpanelAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val authController: AuthController,
    private val profileController: ProfileController,
    private val userManager: UserManager,
    private val exchange: Exchange,
    private val balanceController: BalanceController,
    private val notificationCollectionHistory: NotificationCollectionHistoryController,
    private val inMemoryDao: InMemoryDao,
    private val analytics: AnalyticsService,
    private val mnemonicManager: com.getcode.services.manager.MnemonicManager,
    private val mixpanelAPI: MixpanelAPI
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private var softLoginDisabled: Boolean = false

    companion object {
        private const val TAG = "AuthManager"
        internal fun taggedTrace(message: String, type: TraceType = TraceType.Log) {
            trace(message = message, type = type, tag = TAG)
        }
    }

    @SuppressLint("CheckResult")
    fun init(onInitialized: () -> Unit = { }) {
        launch {
            val token = AccountUtils.getToken(context)?.token
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
        val entropyB64 = SessionManager.entropyB64
        return if (entropyB64 == null) {
            val seedB64 = Ed25519.createSeed16().encodeBase64()
            sessionManager.set(seedB64)
            return seedB64
        } else {
            entropyB64
        }
    }

    suspend fun createAccount(
        displayName: String,
        rollbackOnError: Boolean = false,
    ): Result<ID> {
        val entropyB64 = setupAsNew()
        if (entropyB64.isEmpty()) {
            taggedTrace("provided entropy was empty", type = TraceType.Error)
            sessionManager.clear()
            return Result.failure(Throwable("Provided entropy was empty"))
        }

        softLoginDisabled = true

        if (!CodeAppDatabase.isOpen()) {
            CodeAppDatabase.init(context, entropyB64)
            Database.register(CodeAppDatabase.requireInstance())
        }

        FlipchatServices.openDatabase(context, entropyB64)

        val originalSessionState = SessionManager.authState.value
        sessionManager.set(entropyB64)
        SessionManager.getOrganizer()?.ownerKeyPair?.let {
            // relay owner keypair to user manager
            userManager.set(keyPair = it)
        }

        return authController.register(displayName)
            .onSuccess {
                AccountUtils.addAccount(
                    context = context,
                    name = displayName,
                    password = it.base58,
                    token = entropyB64
                )
                userManager.set(displayName = displayName)
                userManager.set(userId = it)
            }
            .onFailure {
                softLoginDisabled = false
                if (rollbackOnError) {
                    login(
                        originalSessionState.entropyB64.orEmpty(),
                        false,
                        rollbackOnError = false
                    )
                } else {
                    clearToken()
                }
            }
    }

    suspend fun login(
        entropyB64: String,
        isSoftLogin: Boolean = false,
        rollbackOnError: Boolean = false
    ): Result<ID> {
        taggedTrace("Login: isSoftLogin: $isSoftLogin, rollbackOnError: $rollbackOnError")

        if (entropyB64.isEmpty()) {
            taggedTrace("provided entropy was empty", type = TraceType.Error)
            sessionManager.clear()
            return Result.failure(Throwable("Provided entropy was empty"))
        }

        if (!CodeAppDatabase.isOpen()) {
            CodeAppDatabase.init(context, entropyB64)
            Database.register(CodeAppDatabase.requireInstance())
        }

        FlipchatServices.openDatabase(context, entropyB64)

        val originalSessionState = SessionManager.authState.value
        sessionManager.set(entropyB64)
        SessionManager.getOrganizer()?.ownerKeyPair?.let {
            // relay owner keypair to user manager
            userManager.set(keyPair = it)
        }


        if (!isSoftLogin) {
            loginAnalytics(entropyB64)
        }

        if (!isSoftLogin) softLoginDisabled = true

        // Add back once server persistence is in
//        val ret = if (isSoftLogin) {
//            val userId = AccountUtils.getUserId(context)
//            userId?.let {
//                Result.success(Base58.decode(it).toList())
//            } ?: authController.login()
//        } else {
//
//        }

        return authController.login()
            .map { it to profileController.getProfile(it) }
            .map { (id, profileResult) ->
                profileResult.onSuccess { userManager.set(displayName = it.displayName) }
                id
            }
            .onSuccess {
                userManager.set(userId = it)
            }
            .onFailure {
                it.printStackTrace()
                if (rollbackOnError) {
                    login(
                        originalSessionState.entropyB64.orEmpty(),
                        isSoftLogin,
                        rollbackOnError = false
                    )
                } else {
                    logout(context)
                    clearToken()
                }
            }
    }

    fun deleteAndLogout(context: Context, onComplete: () -> Unit = {}) {
        //todo: add account deletion
        logout(context, onComplete)
    }

    fun logout(context: Context, onComplete: () -> Unit = {}) {
        launch {
            val token = AccountUtils.getToken(context)
            when (token) {
                is TokenResult.Account -> {
                    AccountUtils.removeAccounts(context)
                        .doOnSuccess { res: Boolean ->
                            if (res) {
                                clearToken()
                                onComplete()
                            }
                        }
                        .subscribe()
                }
                is TokenResult.Code -> {
                    onComplete()
                }
                null -> Unit
            }
        }
    }

    suspend fun logout(context: Context): Result<Unit> {
        return AccountUtils.removeAccounts(context).toFlowable()
            .to {
                runCatching { it.firstOrError().blockingGet() }
            }.onSuccess {
                clearToken()
            }.map { Result.success(Unit) }
    }

    private fun loginAnalytics(entropyB64: String) {
        val owner = mnemonicManager.getKeyPair(entropyB64)
        taggedTrace("analytics login event")
        analytics.login(
            ownerPublicKey = owner.getPublicKeyBase58(),
            autoCompleteCount = 0,
            inputChangeCount = 0
        )
    }

    private fun clearToken() {
        FirebaseMessaging.getInstance().deleteToken()
        analytics.logout()
        sessionManager.clear()
        notificationCollectionHistory.reset()
        inMemoryDao.clear()
        Database.delete(context)
        if (!BuildConfig.DEBUG) Bugsnag.setUser(null, null, null)
    }

    private suspend fun savePrefs() {
        Timber.d("saving prefs")

        updateFcmToken()
        sessionManager.comeAlive()
    }

    @SuppressLint("CheckResult")
    private suspend fun updateFcmToken() {
        if (isMock()) return

        val installationId = Firebase.installations.installationId()
        val pushToken = Firebase.messaging.token() ?: return

        // TODO: add back once push controller is added
//        pushRepository.updateToken(pushToken, installationId)
//            .onSuccess {
//                Timber.d("push token updated")
//            }.onFailure {
//                Timber.e(t = it, message = "Failure updating push token")
//            }
    }

    sealed class AuthManagerException : Exception() {
        class TimelockUnlockedException : AuthManagerException()
    }
}
