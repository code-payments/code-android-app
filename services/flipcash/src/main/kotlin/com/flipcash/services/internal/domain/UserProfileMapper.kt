package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.profile.v1.Model
import com.codeinc.flipcash.gen.profile.v1.emailAddressOrNull
import com.codeinc.flipcash.gen.profile.v1.phoneNumberOrNull
import com.flipcash.services.models.UserProfile
import com.getcode.opencode.mapper.Mapper
import javax.inject.Inject

class UserProfileMapper @Inject constructor(
    private val socialMapper: SocialAccountMapper,
): Mapper<Model.UserProfile, UserProfile> {
    override fun map(from: Model.UserProfile): UserProfile {
        return UserProfile(
            displayName = from.displayName,
            socialAccounts = from.socialProfilesList.mapNotNull { socialMapper.map(it) },
            verifiedPhoneNumber = from.phoneNumberOrNull?.value,
            verifiedEmailAddress = from.emailAddressOrNull?.value,
        )
    }
}