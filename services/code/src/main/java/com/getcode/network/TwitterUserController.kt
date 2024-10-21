package com.getcode.network

import com.getcode.manager.SessionManager
import com.getcode.model.TwitterUser
import com.getcode.network.client.Client
import com.getcode.network.client.fetchTwitterUser
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.services.utils.getOrPutIfNonNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitterUserController @Inject constructor(
    private val client: Client,
    betaFlags: BetaFlagsRepository,
    private val prefRepository: PrefRepository,
) {
    private var cachedUsers = mutableMapOf<String, TwitterUser>()


    suspend fun fetchUser(username: String, ignoreCache: Boolean = false): TwitterUser? {
        val organizer = SessionManager.getOrganizer() ?: return null
        val key = username.lowercase()

        if (ignoreCache) {
            val user = client.fetchTwitterUser(organizer, username).getOrThrow()
            cachedUsers[key] = user
            return user
        }

        return cachedUsers.getOrPutIfNonNull(key) {
            Timber.d("fetching user $username")
            client.fetchTwitterUser(organizer, username).getOrThrow()
        }
    }
}