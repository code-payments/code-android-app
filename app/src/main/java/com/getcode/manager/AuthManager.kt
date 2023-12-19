package com.getcode.manager

import android.app.Activity
import android.content.Context
import com.bugsnag.android.Bugsnag
import com.getcode.App
import com.getcode.BuildConfig
import com.getcode.crypt.MnemonicPhrase
import com.google.firebase.messaging.FirebaseMessaging
import com.getcode.db.Database
import com.getcode.db.InMemoryDao
import com.getcode.ed25519.Ed25519
import com.getcode.model.AirdropType
import com.getcode.model.PrefsBool
import com.getcode.model.PrefsString
import com.getcode.network.BalanceController
import com.getcode.network.client.Client
import com.getcode.network.repository.*
import com.getcode.util.AccountUtils
import com.getcode.util.showNetworkError
import com.getcode.utils.ErrorUtils
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    private val sessionManager: SessionManager,
    private val phoneRepository: PhoneRepository,
    private val identityRepository: IdentityRepository,
    private val pushRepository: PushRepository,
    private val prefRepository: PrefRepository,
    private val currencyRepository: CurrencyRepository,
    private val balanceController: BalanceController,
    private val inMemoryDao: InMemoryDao,
    private val analyticsManager: AnalyticsManager,
    private val client: Client
) {
    private var softLoginDisabled: Boolean = false

    fun init(activity: Activity) {
        AccountUtils
            .getToken(activity)
            .subscribeOn(Schedulers.computation())
            .flatMapCompletable(::softLogin)
            .subscribeOn(Schedulers.computation())
            .subscribe({}, ErrorUtils::handleError)
    }

    private fun softLogin(entropyB64: String): Completable {
        if (softLoginDisabled) return Completable.complete()
        return login(App.getInstance(), entropyB64, isSoftLogin = true)
    }

    fun login(context: Context, entropyB64: String, isSoftLogin: Boolean = false, rollbackOnError: Boolean = false): Completable {
        Timber.i("Login: entropyB64: $entropyB64, isSoftLogin: $isSoftLogin, rollbackOnError: $rollbackOnError")

        if (entropyB64.isEmpty()) {
            sessionManager.clear()
            return Completable.complete()
        }

        return Single.create<SessionManager.SessionState> {
            if (!isSoftLogin) softLoginDisabled = true

            Database.init(App.getInstance(), entropyB64)

            val originalSessionState = SessionManager.authState.value
            sessionManager.set(App.getInstance(), entropyB64)

            if (!isSoftLogin) {
                loginAnalytics(entropyB64)
            }
            it.onSuccess(originalSessionState ?: SessionManager.SessionState())
        }
            .flatMapCompletable {
                val fetchData = fetchAdditionalAccountData(context, entropyB64, isSoftLogin, rollbackOnError, it)
                if (isSoftLogin) {
                    fetchData.subscribe({}, ErrorUtils::handleError)
                    Completable.complete()
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
                        context = App.getInstance(),
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
                        login(context, originalSessionState?.entropyB64.orEmpty(), isSoftLogin, rollbackOnError = false)
                    } else {
                        clearToken()
                    }
                } else {
                    if (isTimelockUnlockedException) {
                        SessionManager.authStateMutable.update { state -> state?.copy(isTimelockUnlocked = true) }
                    }
                    if (ErrorUtils.isNetworkError(it) || ErrorUtils.isRuntimeError(it)) {
                        ErrorUtils.showNetworkError()
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
        AccountUtils.removeAccounts(activity)
            .doOnSuccess { res: Boolean ->
                if (res) {
                    clearToken()
                    onComplete()
                }
            }
            .subscribe()
    }


    private fun fetchData(context: Context, entropyB64: String):
            Single<Pair<PhoneRepository.GetAssociatedPhoneNumberResponse, IdentityRepository.GetUserResponse>> {

        var owner = SessionManager.authState.value?.keyPair
        if (owner == null || SessionManager.authState.value?.entropyB64 != entropyB64) {
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
                if (SessionManager.authState.value?.entropyB64 != entropyB64) {
                    sessionManager.set(App.getInstance(), entropyB64)
                }
                balanceController.fetchBalance()
                    .toSingleDefault(Pair(phone!!, user!!))
            }
            .doOnSuccess {
                savePrefs(phone!!, user!!)
                updateFcmToken(owner, user!!.dataContainerId.toByteArray())
                currencyRepository.fetchRates()
                if (!BuildConfig.DEBUG) Bugsnag.setUser(null, phone?.phoneNumber, null)
            }
    }

    private fun loginAnalytics(entropyB64: String) {
        val owner = MnemonicPhrase.fromEntropyB64(App.getInstance(), entropyB64).getSolanaKeyPair(App.getInstance())
        analyticsManager.login(
            ownerPublicKey = owner.getPublicKeyBase58(),
            autoCompleteCount = 0,
            inputChangeCount = 0
        )
    }

    private fun clearToken() {
        FirebaseMessaging.getInstance().deleteToken()
        analyticsManager.logout()
        sessionManager.clear()
        Database.close()
        inMemoryDao.clear()
        Database.delete(App.getInstance())
        if (!BuildConfig.DEBUG) Bugsnag.setUser(null, null, null)
    }

    private fun savePrefs(
        phone: PhoneRepository.GetAssociatedPhoneNumberResponse,
        user: IdentityRepository.GetUserResponse
    ) {
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
        phoneRepository.phoneLinked = phone.isLinked

        prefRepository.set(
            PrefsBool.IS_DEBUG_ALLOWED,
            user.enableDebugOptions,
        )
        prefRepository.set(
            PrefsBool.IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP,
            user.eligibleAirdrops.contains(AirdropType.GetFirstKin),
        )
        prefRepository.set(
            PrefsBool.IS_ELIGIBLE_GIVE_FIRST_KIN_AIRDROP,
            user.eligibleAirdrops.contains(AirdropType.GiveFirstKin),
        )
    }

    private fun updateFcmToken(keyPair: Ed25519.KeyPair, containerId: ByteArray) {
        if (isMock()) return

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            pushRepository.addToken(keyPair, containerId, token).subscribe({}, ErrorUtils::handleError)
        }
    }

    sealed class AuthManagerException: Exception() {
        class PhoneInvalidException: AuthManagerException()
        class TimelockUnlockedException: AuthManagerException()
    }
}
