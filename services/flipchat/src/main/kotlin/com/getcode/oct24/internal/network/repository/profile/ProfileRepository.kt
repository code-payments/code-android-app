package com.getcode.oct24.internal.network.repository.profile

import com.getcode.model.ID
import com.getcode.oct24.domain.model.profile.UserProfile
import com.getcode.oct24.internal.data.mapper.ProfileMapper
import com.getcode.oct24.internal.network.service.ProfileService
import com.getcode.oct24.user.UserManager
import com.getcode.utils.ErrorUtils
import javax.inject.Inject

interface ProfileRepository {
    suspend fun getProfile(userId: ID): Result<UserProfile>
    suspend fun setDisplayName(name: String): Result<Unit>
}