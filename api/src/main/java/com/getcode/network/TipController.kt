package com.getcode.network

import com.getcode.manager.SessionManager
import com.getcode.model.CodePayload
import com.getcode.model.PrefsBool
import com.getcode.model.PrefsString
import com.getcode.model.TipMetadata
import com.getcode.model.TwitterUser
import com.getcode.network.client.Client
import com.getcode.network.client.fetchTwitterUser
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.TwitterUserFetchError
import com.getcode.network.repository.base58
import com.getcode.utils.bytes
import com.getcode.utils.getOrPutIfNonNull
import com.getcode.vendor.Base58
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.Timer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.fixedRateTimer

typealias TipUser = Pair<String, CodePayload>

@Singleton
class TipController @Inject constructor(
    private val client: Client,
    betaFlags: BetaFlagsRepository,
    private val prefRepository: PrefRepository,
) {

    companion object {
        private const val POLL_FREQUENCY_LOOKING_SECS = 5L
    }

    private var pollTimer: Timer? = null
    private var lastPoll: Long = 0L
    private val scope = CoroutineScope(Dispatchers.IO)

    private var cachedUsers = mutableMapOf<String, TwitterUser>()

    var scannedUserData: TipUser? = null
        private set
    var userMetadata: TwitterUser? = null
        private set

    val connectedAccount: StateFlow<TipMetadata?> = prefRepository.observeOrDefault(PrefsString.KEY_TIP_ACCOUNT, "")
        .map { runCatching { Json.decodeFromString<TwitterUser>(it) }.getOrNull() }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val verificationInProgress: StateFlow<Boolean> = prefRepository.observeOrDefault(PrefsBool.STARTED_TIP_CONNECT, false)
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    val showTwitterSplat: Flow<Boolean> =
        combine(
            connectedAccount,
            betaFlags.observe().map { it.tipsEnabled },
            prefRepository.observeOrDefault(PrefsBool.SEEN_TIP_CARD, false)
        ) { connected, tipsEnabled, seen ->
            connected != null && !seen && tipsEnabled
        }

    private fun startPollTimer() {
        if (connectedAccount.value != null) return

        Timber.d("twitter poll start")
        pollTimer?.cancel()
        pollTimer =
            fixedRateTimer("twitterPollTimer", false, 0, 1000 * POLL_FREQUENCY_LOOKING_SECS) {
                scope.launch {
                    val time = System.currentTimeMillis()
                    val isPastThrottle =
                        time - lastPoll > 1000 * (POLL_FREQUENCY_LOOKING_SECS / 2.0) || lastPoll == 0L

                    if (isPastThrottle) {
                        callForConnectedUser()
                    }
                }
            }
    }

    private suspend fun callForConnectedUser() {
        Timber.d("twitter poll call")
        val tipAddress = SessionManager.getOrganizer()?.primaryVault ?: return
        // only set lastPoll if we actively attempt to reach RPC
        lastPoll = System.currentTimeMillis()
        client.fetchTwitterUser(tipAddress)
            .onSuccess {
                Timber.d("current user twitter connected @ ${it.username}")
                prefRepository.set(PrefsString.KEY_TIP_ACCOUNT, Json.encodeToString(it))
                stopTimer()
            }
            .onFailure {
                when (it) {
                    is TwitterUserFetchError -> {
                        prefRepository.set(PrefsString.KEY_TIP_ACCOUNT, "")
                    }
                }
            }
    }

    init {
        SessionManager.authState
            .map { it.organizer }
            .filterNotNull()
            .map { it.primaryVault }
            .onEach { checkForConnection() }
            .launchIn(scope)
    }

    fun checkForConnection() {
        if (!verificationInProgress.value) {
            scope.launch {
                callForConnectedUser()
            }
            return
        }
        startPollTimer()
    }

    suspend fun fetch(username: String, payload: CodePayload) {
        val metadata = fetch(username)
        scannedUserData = username to payload
        userMetadata = metadata
    }

    suspend fun fetch(username: String): TwitterUser? {
        val key = username.lowercase()
        return cachedUsers.getOrPutIfNonNull(key) {
            Timber.d("fetching user $username")
            client.fetchTwitterUser(username).getOrThrow()
        }
    }

    fun reset() {
        scannedUserData = null
        userMetadata = null
    }

    fun seenTipCardBanner() {
        prefRepository.set(PrefsBool.DISMISSED_TIP_CARD_BANNER, true)
        endVerification()
    }

    fun clearTwitterSplat() {
        prefRepository.set(PrefsBool.SEEN_TIP_CARD, true)
    }

    fun generateTipVerification(): String? {
        val authority = SessionManager.getOrganizer()?.tray?.owner?.getCluster()?.authority
        val tipAddress = SessionManager.getOrganizer()?.primaryVault
            ?.let { Base58.encode(it.byteArray) }

        if (tipAddress != null && authority != null) {
            val nonce = UUID.randomUUID()
            val signature = authority.keyPair.sign(nonce.bytes.toByteArray())
            val verificationMessage = listOf(
                "CodeAccount",
                tipAddress,
                Base58.encode(nonce.bytes.toByteArray()),
                signature.base58
            ).joinToString(":")

            return verificationMessage
        }

        return null
    }

    fun startVerification() {
        prefRepository.set(PrefsBool.STARTED_TIP_CONNECT, true)
    }

    private fun endVerification() {
        prefRepository.set(PrefsBool.STARTED_TIP_CONNECT, false)
    }

    fun stopTimer() {
        stopTimerInternal()
    }

    private fun stopTimerInternal() {
        Timber.d("twitter poll stop")
        pollTimer?.cancel()
    }
}