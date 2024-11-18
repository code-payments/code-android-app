package xyz.flipchat.app.auth

import android.annotation.SuppressLint
import android.content.Context
import com.bugsnag.android.Bugsnag
import com.getcode.ed25519.Ed25519
import com.getcode.model.ID
import xyz.flipchat.app.util.AccountUtils
import xyz.flipchat.app.util.TokenResult
import com.getcode.services.db.Database
import com.getcode.services.utils.installationId
import com.getcode.services.utils.token
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.base58
import com.getcode.utils.encodeBase64
import com.getcode.utils.trace
import com.google.firebase.Firebase
import com.google.firebase.installations.installations
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.messaging
import com.ionspin.kotlin.crypto.LibsodiumInitializer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import xyz.flipchat.FlipchatServices
import xyz.flipchat.app.BuildConfig
import xyz.flipchat.controllers.AuthController
import xyz.flipchat.controllers.ProfileController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authController: AuthController,
    private val profileController: ProfileController,
    private val userManager: xyz.flipchat.services.user.UserManager,
//    private val balanceController: BalanceController,
//    private val notificationCollectionHistory: NotificationCollectionHistoryController,
//    private val analytics: AnalyticsService,
//    private val mixpanelAPI: MixpanelAPI
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
        val entropyB64 = userManager.entropy
        return if (entropyB64 == null) {
            val seedB64 = Ed25519.createSeed16().encodeBase64()
            userManager.establish(seedB64)
            return seedB64
        } else {
            entropyB64
        }
    }

    suspend fun createAccount(
        displayName: String,
    ): Result<ID> {
        val entropyB64 = setupAsNew()
        if (entropyB64.isEmpty()) {
            taggedTrace("provided entropy was empty", type = TraceType.Error)
            userManager.clear()
            return Result.failure(Throwable("Provided entropy was empty"))
        }

        softLoginDisabled = true

        FlipchatServices.openDatabase(context, entropyB64)

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
                it.printStackTrace()
                softLoginDisabled = false
                clearToken()
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
            userManager.clear()
            return Result.failure(Throwable("Provided entropy was empty"))
        }

        FlipchatServices.openDatabase(context, entropyB64)

        val originalEntropy = userManager.entropy
        userManager.establish(entropy = entropyB64)


        if (!isSoftLogin) {
            loginAnalytics()
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
                        originalEntropy.orEmpty(),
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

    private fun loginAnalytics() {
        taggedTrace("analytics login event")
//        analytics.login(
//            ownerPublicKey = owner.getPublicKeyBase58(),
//            autoCompleteCount = 0,
//            inputChangeCount = 0
//        )
    }

    private fun clearToken() {
        FirebaseMessaging.getInstance().deleteToken()
//        analytics.logout()
        userManager.clear()
//        notificationCollectionHistory.reset()
//        inMemoryDao.clear()
        Database.delete(context)
        if (!BuildConfig.DEBUG) Bugsnag.setUser(null, null, null)
    }

    private suspend fun savePrefs() {
        Timber.d("saving prefs")

        updateFcmToken()
    }

    @SuppressLint("CheckResult")
    private suspend fun updateFcmToken() {
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
