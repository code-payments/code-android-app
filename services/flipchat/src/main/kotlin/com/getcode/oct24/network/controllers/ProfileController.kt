package com.getcode.oct24.network.controllers

import com.getcode.model.ID
import com.getcode.oct24.domain.model.profile.UserProfile
import com.getcode.oct24.internal.network.repository.profile.ProfileRepository
import javax.inject.Inject

class ProfileController @Inject constructor(
    private val repository: ProfileRepository
) {

    suspend fun getProfile(userId: ID): Result<UserProfile> {
        return repository.getProfile(userId)
    }

    suspend fun setDisplayName(name: String): Result<Unit> {
        return repository.setDisplayName(name)
    }
}