package com.getcode.network

import com.getcode.model.CodePayload
import com.getcode.model.TwitterUser
import com.getcode.network.client.Client
import com.getcode.network.client.fetchTwitterUser
import com.getcode.utils.getOrPutIfNonNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

typealias TipUser = Pair<String, CodePayload>

@Singleton
class TipController @Inject constructor(
    private val client: Client,
) {
    var scannedUserData: TipUser? = null
        private set
    var userMetadata: TwitterUser? = null
        private set

    private var cachedUsers = mutableMapOf<String, TwitterUser>()

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