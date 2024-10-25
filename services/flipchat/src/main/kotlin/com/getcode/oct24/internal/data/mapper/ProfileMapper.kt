package com.getcode.oct24.internal.data.mapper

import com.codeinc.flipchat.gen.profile.v1.Model
import com.getcode.oct24.domain.model.profile.UserProfile
import com.getcode.services.mapper.Mapper
import javax.inject.Inject

class ProfileMapper @Inject constructor(): Mapper<Model.UserProfile, UserProfile> {
    override fun map(from: Model.UserProfile): UserProfile {
        return UserProfile(displayName = from.displayName)
    }
}