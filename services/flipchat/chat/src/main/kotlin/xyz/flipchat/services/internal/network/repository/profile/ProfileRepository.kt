package xyz.flipchat.services.internal.network.repository.profile

import com.getcode.model.ID
import xyz.flipchat.services.domain.model.profile.UserProfile

interface ProfileRepository {
    suspend fun getProfile(userId: ID): Result<UserProfile>
    suspend fun setDisplayName(name: String): Result<Unit>
}