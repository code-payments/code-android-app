package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.toSerialized
import com.flipcash.app.persistence.sources.mapper.toDomain
import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.core.ID
import com.getcode.utils.hexEncodedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read access to the normalized `user_profiles` cache, keyed by user id. Enables fast,
 * offline-friendly profile lookups (e.g. resolving a push notification's userId substitution)
 * without a network round-trip.
 */
@Singleton
class UserProfileDataSource @Inject constructor() {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    /** The cached display name for [userId], or null if the user isn't cached (or has no name). */
    suspend fun getCachedDisplayName(userId: ID): String? {
        val entity = db?.userProfileDao()?.getByUserId(userId.hexEncodedString()) ?: return null
        // toSerialized() also resolves rows still carrying a not-yet-backfilled migration blob.
        return entity.toSerialized().displayName?.takeIf { it.isNotBlank() }
    }

    /** The cached [UserProfile] for [userId], or null if the user isn't cached. */
    suspend fun getCachedProfile(userId: ID): UserProfile? {
        val entity = db?.userProfileDao()?.getByUserId(userId.hexEncodedString()) ?: return null
        return entity.toSerialized().toDomain()
    }

    /**
     * Stores [profile]'s name + avatar for [userId] (INSERT OR REPLACE, preserving any existing
     * phone/email/social columns). Used to back-fill the cache after a network resolve so
     * [observeProfiles] re-emits and consumers (e.g. the transaction list) resolve the row live.
     */
    suspend fun store(userId: ID, profile: UserProfile) {
        db?.userProfileDao()?.upsertNameAndAvatar(
            userIdHex = userId.hexEncodedString(),
            displayName = profile.displayName,
            profilePicture = profile.profilePicture,
        )
    }

    /**
     * Observes the cached profiles as a `user-id-hex → `[UserProfile] map. Re-emits as profiles are
     * added/updated, so consumers (e.g. the transaction list) resolve counterparties reactively from
     * memory — no per-item I/O.
     *
     * The DB is resolved at **collection** time (not when this is called) and we wait for it to
     * become available: the per-user DB is created on login, but consumers like the wallet
     * coordinator are `@Singleton`s that may build their flow graph *before* login. Capturing a null
     * `db` once (the previous `?: flowOf(emptyMap())`) permanently pinned the stream to empty — so
     * avatars/names never resolved even after the DB was ready. Deferring + polling fixes that.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeProfiles(): Flow<Map<String, UserProfile>> =
        FlipcashDatabase.observeInstance()
            .flatMapLatest { database ->
                database?.userProfileDao()?.observeAll()?.map { rows ->
                    rows.associate { it.userIdHex to it.toSerialized().toDomain() }
                } ?: flowOf(emptyMap())
            }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
}
