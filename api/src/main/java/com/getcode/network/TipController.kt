package com.getcode.network

import com.getcode.manager.SessionManager
import com.getcode.model.CodePayload
import com.getcode.model.PrefsBool
import com.getcode.model.PrefsString
import com.getcode.model.TwitterUser
import com.getcode.network.client.Client
import com.getcode.network.client.fetchTwitterUser
import com.getcode.network.repository.PrefRepository
import com.getcode.utils.combine
import com.getcode.utils.getOrPutIfNonNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Timer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.fixedRateTimer

typealias TipUser = Pair<String, CodePayload>

@Singleton
class TipController @Inject constructor(
    private val client: Client,
    private val prefRepository: PrefRepository,
) {

    private var pollTimer: Timer? = null
    private var lastPoll: Long = 0L
    private val scope = CoroutineScope(Dispatchers.IO)

    val connectedAccount: StateFlow<String?> = prefRepository.observeOrDefault(PrefsString.KEY_TWITTER_USERNAME, "")
        .map {
            it.ifEmpty { null }
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val showTwitterSplat: Flow<Boolean> =
        combine(
            connectedAccount,
            prefRepository.observeOrDefault(PrefsBool.SEEN_TIP_CARD, false)
        ) { connected, seen ->
            connected != null && !seen
        }

    var scannedUserData: TipUser? = null
        private set
    var userMetadata: TwitterUser? = null
        private set

    private var cachedUsers = mutableMapOf<String, TwitterUser>()

    private fun startPollTimer() {
        pollTimer?.cancel()
        pollTimer = fixedRateTimer("twitterPollTimer", false, 0, 1000 * 20) {
            scope.launch {
                val time = System.currentTimeMillis()
                val isPastThrottle = time - lastPoll > 1000 * 10 || lastPoll == 0L

                if (connectedAccount.value == null && isPastThrottle) {
                    callForConnectedUser()
                    lastPoll = time
                }
            }
        }
    }

    private suspend fun callForConnectedUser() {
        val tipAddress = SessionManager.getOrganizer()?.primaryVault ?: return
        client.fetchTwitterUser(tipAddress)
            .onSuccess {
                Timber.d("current user twitter connected @ ${it.username}")
                prefRepository.set(PrefsString.KEY_TWITTER_USERNAME, it.username)
            }
            .onFailure {
                prefRepository.set(PrefsString.KEY_TWITTER_USERNAME, "")
            }
    }

    fun checkForConnection() {
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

    fun clearTwitterSplat() {
        prefRepository.set(PrefsBool.SEEN_TIP_CARD, true)
    }

    fun stopTimer() {
        pollTimer?.cancel()
    }
}