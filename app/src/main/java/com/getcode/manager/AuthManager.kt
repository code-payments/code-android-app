package com.getcode.manager

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import com.bugsnag.android.Bugsnag
import com.getcode.BuildConfig
import com.getcode.analytics.AnalyticsService
import com.getcode.crypt.MnemonicPhrase
import com.getcode.db.Database
import com.getcode.db.InMemoryDao
import com.getcode.model.AirdropType
import com.getcode.model.PrefsBool
import com.getcode.model.PrefsString
import com.getcode.network.BalanceController
import com.getcode.network.HistoryController
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.network.repository.IdentityRepository
import com.getcode.network.repository.PhoneRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.PushRepository
import com.getcode.network.repository.encodeBase64
import com.getcode.network.repository.getPublicKeyBase58
import com.getcode.network.repository.isMock
import com.getcode.util.AccountUtils
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.installationId
import com.getcode.utils.trace
import com.getcode.utils.token
import com.google.firebase.Firebase
import com.google.firebase.installations.installations
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.messaging
import com.ionspin.kotlin.crypto.LibsodiumInitializer
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
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
    private val phoneRepository: PhoneRepository,
    private val identityRepository: IdentityRepository,
    private val pushRepository: PushRepository,
    private val prefRepository: PrefRepository,
    private val betaFlags: BetaFlagsRepository,
    private val exchange: Exchange,
    private val balanceController: BalanceController,
    private val historyController: HistoryController,
    private val inMemoryDao: InMemoryDao,
    private val analytics: AnalyticsService,
    private val mnemonicManager: MnemonicManager,
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
            val token = AccountUtils.getToken(context)
            softLogin(token.orEmpty())
                .subscribeOn(Schedulers.computation())
                .doOnComplete { LibsodiumInitializer.initializeWithCallback(onInitialized) }
                .subscribe({ }, ErrorUtils::handleError)
        }
    }

    private fun softLogin(entropyB64: String): Completable {
        if (softLoginDisabled) return Completable.complete()
        return login(entropyB64, isSoftLogin = true)
    }

    fun login(
        entropyB64: String,
        isSoftLogin: Boolean = false,
        rollbackOnError: Boolean = false
    ): Completable {
        taggedTrace("Login: isSoftLogin: $isSoftLogin, rollbackOnError: $rollbackOnError")

        if (entropyB64.isEmpty()) {
            taggedTrace("provided entropy was empty", type = TraceType.Error)
            sessionManager.clear()
            return Completable.complete()
        }

        return Single.create {
            if (!isSoftLogin) softLoginDisabled = true

            if (!Database.isOpen()) {
                Database.init(context, entropyB64)
            }

            val originalSessionState = SessionManager.authState.value
            sessionManager.set(entropyB64)

            if (!isSoftLogin) {
                loginAnalytics(entropyB64)
            }
            it.onSuccess(originalSessionState)
        }.flatMapCompletable {
            val fetchData =
                fetchAdditionalAccountData(context, entropyB64, isSoftLogin, rollbackOnError, it)
            if (isSoftLogin) {
                fetchData.onErrorComplete {
                    ErrorUtils.handleError(it)
                    true
                }
            } else {
                fetchData
            }
        }.doOnError { softLoginDisabled = false }
    }

    private fun fetchAdditionalAccountData(
        context: Context,
        entropyB64: String,
        isSoftLogin: Boolean,
        rollbackOnError: Boolean = false,
        originalSessionState: SessionManager.SessionState?
    ): Completable {
        return fetchData(entropyB64)
            .doOnSuccess {
                if (!isSoftLogin) {
                    if (SessionManager.getOrganizer()?.primaryVault == null) {
                        throw AuthManagerException.PhoneInvalidException()
                    }
                    val (phone, _) = it

                    AccountUtils.addAccount(
                        context = context,
                        name = phone.phoneNumber,
                        password = entropyB64,
                        token = entropyB64
                    )
                }
            }
            .doOnError {
                val isTimelockUnlockedException =
                    it is AuthManagerException.TimelockUnlockedException
                if (!isSoftLogin) {
                    if (rollbackOnError) {
                        login(
                            originalSessionState?.entropyB64.orEmpty(),
                            isSoftLogin,
                            rollbackOnError = false
                        )
                    } else {
                        clearToken()
                    }
                } else {
                    if (isTimelockUnlockedException) {
                        SessionManager.update { state -> state.copy(isTimelockUnlocked = true) }
                    }
                }
            }
            .ignoreElement()
    }

    fun deleteAndLogout(context: Context, onComplete: () -> Unit = {}) {
        //todo: add account deletion
        logout(context, onComplete)
    }

    fun logout(context: Context, onComplete: () -> Unit = {}) {
        launch {
            AccountUtils.removeAccounts(context)
                .doOnSuccess { res: Boolean ->
                    if (res) {
                        clearToken()
                        onComplete()
                    }
                }
                .subscribe()
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


    private fun fetchData(entropyB64: String):
            Single<Pair<PhoneRepository.GetAssociatedPhoneNumberResponse, IdentityRepository.GetUserResponse>> {

        taggedTrace("fetching account data")

        var owner = SessionManager.authState.value.keyPair
        if (owner == null || SessionManager.authState.value.entropyB64 != entropyB64) {
            owner = MnemonicPhrase.fromEntropyB64(entropyB64).getSolanaKeyPair()
        }

        var phone: PhoneRepository.GetAssociatedPhoneNumberResponse? = null
        var user: IdentityRepository.GetUserResponse? = null

        return phoneRepository.fetchAssociatedPhoneNumber(owner)
            .firstOrError()
            .subscribeOn(Schedulers.computation())
            .map {
                if (it.isUnlocked) throw AuthManagerException.TimelockUnlockedException()
                if (!it.isSuccess) throw AuthManagerException.PhoneInvalidException()
                it
            }
            .flatMap {
                phone = it
                identityRepository.getUser(owner, it.phoneNumber)
                    .firstOrError()
            }
            .flatMap {
                user = it
                if (SessionManager.authState.value.entropyB64 != entropyB64) {
                    sessionManager.set(entropyB64)
                }
                balanceController.fetchBalance()
                    .toSingleDefault(Pair(phone!!, user!!))
            }
            .doOnSuccess {
                taggedTrace("account data fetched successfully")
                if (!BuildConfig.DEBUG) {
                    if (Bugsnag.isStarted()) {
                        Bugsnag.setUser(null, phone?.phoneNumber, null)
                    }
                }
                launch { savePrefs(phone!!, user!!) }
                launch { exchange.fetchRatesIfNeeded() }
                launch { historyController.fetchChats() }
            }
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
        Database.close()
        historyController.reset()
        inMemoryDao.clear()
        Database.delete(context)
        if (!BuildConfig.DEBUG) Bugsnag.setUser(null, null, null)
    }

    private suspend fun savePrefs(
        phone: PhoneRepository.GetAssociatedPhoneNumberResponse,
        user: IdentityRepository.GetUserResponse
    ) {
        Timber.d("saving prefs")
        phoneRepository.phoneNumber = phone.phoneNumber

        prefRepository.set(
            PrefsString.KEY_USER_ID to user.userId.toByteArray().encodeBase64(),
            PrefsString.KEY_DATA_CONTAINER_ID to user.dataContainerId.toByteArray().encodeBase64(),
        )
        phoneRepository.phoneLinked.value = phone.isLinked

        betaFlags.enableBeta(user.enableDebugOptions)

        Timber.d("airdrops eligible = ${user.eligibleAirdrops.joinToString { it.name }}")
        prefRepository.set(
            PrefsBool.IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP,
            user.eligibleAirdrops.contains(AirdropType.GetFirstKin),
        )
        prefRepository.set(
            PrefsBool.IS_ELIGIBLE_GIVE_FIRST_KIN_AIRDROP,
            user.eligibleAirdrops.contains(AirdropType.GiveFirstKin),
        )

        updateFcmToken()
        sessionManager.comeAlive()
    }

    @SuppressLint("CheckResult")
    private suspend fun updateFcmToken() {
        if (isMock()) return

        val installationId = Firebase.installations.installationId()
        val pushToken = Firebase.messaging.token() ?: return

        pushRepository.updateToken(pushToken, installationId)
            .onSuccess {
                Timber.d("push token updated")
            }.onFailure {
                Timber.e(t = it, message = "Failure updating push token")
            }
    }

    sealed class AuthManagerException : Exception() {
        class PhoneInvalidException : AuthManagerException()
        class TimelockUnlockedException : AuthManagerException()
    }
}
