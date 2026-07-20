package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.profile.v1.Model
import com.codeinc.flipcash.gen.profile.v1.emailAddressOrNull
import com.codeinc.flipcash.gen.profile.v1.phoneNumberOrNull
import com.flipcash.services.internal.network.extensions.toMediaItem
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.VerifiableContactMethod
import com.getcode.opencode.mapper.Mapper
import javax.inject.Inject

class UserProfileMapper @Inject constructor(
    private val socialMapper: SocialAccountMapper,
): Mapper<Model.UserProfile, UserProfile> {
    override fun map(from: Model.UserProfile): UserProfile {
        return UserProfile(
            displayName = from.displayName,
            socialAccounts = from.socialProfilesList.mapNotNull { socialMapper.map(it) },
            // A value is only present on the server profile once verified.
            phoneNumber = from.phoneNumberOrNull?.value?.let { VerifiableContactMethod(it, verified = true) },
            email = from.emailAddressOrNull?.value?.let { VerifiableContactMethod(it, verified = true) },
            profilePicture = if (from.hasProfilePicture()) from.profilePicture.toMediaItem() else null,
        )
    }
}