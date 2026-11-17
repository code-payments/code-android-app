package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.profile.v1.Model
import com.codeinc.flipcash.gen.profile.v1.emailAddressOrNull
import com.codeinc.flipcash.gen.profile.v1.phoneNumberOrNull
import com.codeinc.flipcash.gen.profile.v1.usernameOrNull
import com.flipcash.services.internal.network.extensions.toFiat
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.internal.network.extensions.toMediaItem
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.VerifiableContactMethod
import com.getcode.opencode.mapper.Mapper
import javax.inject.Inject
import kotlin.time.Instant

class UserProfileMapper @Inject constructor(
    private val socialMapper: SocialAccountMapper,
): Mapper<Model.UserProfile, UserProfile> {
    override fun map(from: Model.UserProfile): UserProfile {
        return UserProfile(
            displayName = from.displayName,
            socialAccounts = from.socialProfilesList.mapNotNull { socialMapper.map(it) },
            // A phone number is only present on the server profile once verified.
            phoneNumber = from.phoneNumberOrNull?.value?.let { VerifiableContactMethod(it, verified = true) },
            // An email can be linked without a code (LinkUnverified); the server says which.
            email = from.emailAddressOrNull?.value?.let {
                VerifiableContactMethod(it, verified = from.isEmailAddressVerified)
            },
            profilePicture = if (from.hasProfilePicture()) from.profilePicture.toMediaItem() else null,
            joinedAt = if (from.hasJoinTs()) {
                Instant.fromEpochSeconds(from.joinTs.seconds, from.joinTs.nanos)
            } else null,
            tipCardColor = if (from.hasFlipcardCustomization()) from.flipcardCustomization.color.hex else null,
            userId = if (from.hasUserId()) from.userId.toId() else null,
            // Public, so it is returned for any user — absent only when unclaimed.
            username = from.usernameOrNull?.value,
            minDmChatInitFee = if (from.hasMinDmChatInitFee()) from.minDmChatInitFee.toFiat() else null,
            isUsernameAutoAssigned = from.isUsernameAutoAssigned,
            bio = from.bio,
            coverPicture = if (from.hasCoverPicture()) from.coverPicture.toMediaItem() else null,
            isEmailAddressVerified = from.isEmailAddressVerified,
        )
    }
}