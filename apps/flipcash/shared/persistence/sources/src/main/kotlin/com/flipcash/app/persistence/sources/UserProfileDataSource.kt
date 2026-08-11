package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.toSerialized
import com.getcode.opencode.model.core.ID
import com.getcode.utils.hexEncodedString
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
}
