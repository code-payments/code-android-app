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
import com.getcode.ed25519.Ed25519
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
import com.getcode.utils.installationId
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
import kotlinx.coroutines.reactive.asFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
): CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private var softLoginDisabled: Boolean = false

    @SuppressLint("CheckResult")
    fun init(context: Context, onInitialized: () -> Unit = { }) {
        launch {
            LibsodiumInitializer.initialize()
            val token = AccountUtils.getToken(context)
            softLogin(token.orEmpty())
                .subscribeOn(Schedulers.computation())
                .subscribe(onInitialized, ErrorUtils::handleError)
        }
    }

    private fun softLogin(entropyB64: String): Completable {
        if (softLoginDisabled) return Completable.complete()
        return login(entropyB64, isSoftLogin = true)
    }

    fun login(entropyB64: String, isSoftLogin: Boolean = false, rollbackOnError: Boolean = false): Completable {
        Timber.i("Login: entropyB64: $entropyB64, isSoftLogin: $isSoftLogin, rollbackOnError: $rollbackOnError")

        if (entropyB64.isEmpty()) {
            sessionManager.clear()
            return Completable.complete()
        }

        return Single.create {
            if (!isSoftLogin) softLoginDisabled = true

            if (!Database.isOpen()) {
                Database.init(context, entropyB64)
            }

            val originalSessionState = SessionManager.authState.value
            sessionManager.set(context, entropyB64)

            if (!isSoftLogin) {
                loginAnalytics(entropyB64)
            }
            it.onSuccess(originalSessionState)
        }
            .flatMapCompletable {
                val fetchData = fetchAdditionalAccountData(context, entropyB64, isSoftLogin, rollbackOnError, it)
                if (isSoftLogin) {
                    fetchData.onErrorComplete {
                        ErrorUtils.handleError(it)
                        true
                    }
                } else {
                    fetchData
                }
            }
            .doOnError { softLoginDisabled = false }
    }

    private fun fetchAdditionalAccountData(
        context: Context,
        entropyB64: String,
        isSoftLogin: Boolean,
        rollbackOnError: Boolean = false,
        originalSessionState: SessionManager.SessionState?
    ): Completable {
        return fetchData(context, entropyB64)
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
                val isTimelockUnlockedException = it is AuthManagerException.TimelockUnlockedException
                if (!isSoftLogin) {
                    if (rollbackOnError) {
                        login(originalSessionState?.entropyB64.orEmpty(), isSoftLogin, rollbackOnError = false)
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

    fun deleteAndLogout(activity: Activity, onComplete: () -> Unit = {}) {
        //todo: add account deletion
        logout(activity, onComplete)
    }

    fun logout(activity: Activity, onComplete: () -> Unit = {}) {
        launch {
            AccountUtils.removeAccounts(activity)
                .doOnSuccess { res: Boolean ->
                    if (res) {
                        clearToken()
                        onComplete()
                    }
                }
                .subscribe()
        }
    }

    suspend fun logout(activity: Activity): Result<Unit> {
        return AccountUtils.removeAccounts(activity).toFlowable()
            .to {
                runCatching { it.firstOrError().blockingGet() }
            }.onSuccess {
                clearToken()
            }.map { Result.success(Unit) }
    }


    private fun fetchData(context: Context, entropyB64: String):
            Single<Pair<PhoneRepository.GetAssociatedPhoneNumberResponse, IdentityRepository.GetUserResponse>> {

        var owner = SessionManager.authState.value.keyPair
        if (owner == null || SessionManager.authState.value.entropyB64 != entropyB64) {
            owner = MnemonicPhrase.fromEntropyB64(context, entropyB64).getSolanaKeyPair(context)
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
                    sessionManager.set(context, entropyB64)
                }
                balanceController.fetchBalance()
                    .toSingleDefault(Pair(phone!!, user!!))
            }
            .doOnSuccess {
                savePrefs(phone!!, user!!)
                launch { exchange.fetchRatesIfNeeded() }
                launch { historyController.fetchChats() }
                if (!BuildConfig.DEBUG) Bugsnag.setUser(null, phone?.phoneNumber, null)
            }
    }

    private fun loginAnalytics(entropyB64: String) {
        val owner = MnemonicPhrase.fromEntropyB64(context, entropyB64)
            .getSolanaKeyPair(context)
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
    private fun savePrefs(
        phone: PhoneRepository.GetAssociatedPhoneNumberResponse,
        user: IdentityRepository.GetUserResponse
    ) {
        Timber.d("saving prefs")
        phoneRepository.phoneNumber = phone.phoneNumber
        prefRepository.set(
            Pair(
                PrefsString.KEY_USER_ID,
                user.userId.toByteArray().encodeBase64()
            ),
            Pair(
                PrefsString.KEY_DATA_CONTAINER_ID,
                user.dataContainerId.toByteArray().encodeBase64()
            )
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

        launch { updateFcmToken() }
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

    sealed class AuthManagerException: Exception() {
        class PhoneInvalidException: AuthManagerException()
        class TimelockUnlockedException: AuthManagerException()
    }
}
