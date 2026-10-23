package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.profile.v1.Model
import com.flipcash.services.internal.domain.mapper.Mapper
import com.flipcash.services.models.SocialAccount
import javax.inject.Inject

class SocialAccountMapper @Inject constructor(): Mapper<Model.SocialProfile, SocialAccount?> {
    override fun map(from: Model.SocialProfile): SocialAccount? {
        return when (from.typeCase) {
            Model.SocialProfile.TypeCase.X -> with (from.x) {
                val verificationType = SocialAccount.TwitterX.VerifiedType.entries.getOrNull(verifiedTypeValue)

                SocialAccount.TwitterX(
                    id = id,
                    username = username,
                    name = name,
                    description = description,
                    profilePicUrl = profilePicUrl,
                    verifiedType = verificationType,
                    followerCount = followerCount,
                )
            }

            Model.SocialProfile.TypeCase.TYPE_NOT_SET -> null
        }
    }
}