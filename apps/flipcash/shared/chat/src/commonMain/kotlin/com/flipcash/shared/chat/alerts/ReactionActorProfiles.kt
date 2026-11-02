package com.flipcash.shared.chat.alerts

import com.flipcash.app.persistence.sources.UserProfileDataSource
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.core.ID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The profile of someone who reacted: their name and photo for the alert, from wherever the app
 * already has them, fetching when it has nothing.
 */
fun interface ReactionActorProfiles {
    suspend fun resolve(userId: ID): UserProfile?

    companion object {
        val None = ReactionActorProfiles { null }
    }
}

/**
 * Reads the `user_profiles` cache that every other avatar in the app reads, and on a miss asks the
 * server once and writes the answer back there, so the next alert, and the rest of the app, find it.
 */
@Singleton
internal class StoredReactionActorProfiles @Inject constructor(
    private val userProfiles: UserProfileDataSource,
    private val profileController: ProfileController,
) : ReactionActorProfiles {

    override suspend fun resolve(userId: ID): UserProfile? {
        userProfiles.getCachedProfile(userId)
            ?.takeIf { it.displayName.isNotBlank() || it.profilePicture != null }
            ?.let { return it }
        return profileController.getProfileForUser(userId).getOrNull()
            ?.also { runCatching { userProfiles.store(userId, it) } }
    }
}
