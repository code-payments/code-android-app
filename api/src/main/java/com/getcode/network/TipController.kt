package com.getcode.network

import com.getcode.manager.SessionManager
import com.getcode.model.CodePayload
import com.getcode.model.PrefsString
import com.getcode.model.TwitterUser
import com.getcode.network.client.Client
import com.getcode.network.client.fetchTwitterUser
import com.getcode.network.repository.PrefRepository
import com.getcode.utils.getOrPutIfNonNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

typealias TipUser = Pair<String, CodePayload>

@Singleton
class TipController @Inject constructor(
    private val client: Client,
    private val prefRepository: PrefRepository,
) {

    val connectedAccount: Flow<String?> = prefRepository.observeOrDefault(PrefsString.KEY_TWITTER_USERNAME, "")
        .map {
            it.ifEmpty { null }
        }

    var scannedUserData: TipUser? = null
        private set
    var userMetadata: TwitterUser? = null
        private set

    private var cachedUsers = mutableMapOf<String, TwitterUser>()

    suspend fun checkForConnection() {
        val tipAddress = SessionManager.getOrganizer()?.primaryVault ?: return
        client.fetchTwitterUser(tipAddress)
            .onSuccess {
                Timber.d("current user twitter connected @ ${it.username}")
                prefRepository.set(PrefsString.KEY_TWITTER_USERNAME, it.username)
            }
            .onFailure {
                it.printStackTrace()
                prefRepository.set(PrefsString.KEY_TWITTER_USERNAME, "")
            }
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
}