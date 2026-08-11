package com.flipcash.app.persistence.sources.mapper

import com.flipcash.app.persistence.converters.SocialAccountSerialized
import com.flipcash.app.persistence.converters.UserProfileSerialized
import com.flipcash.services.models.SocialAccount
import com.flipcash.services.models.UserProfile

/**
 * Cached serialized profile → domain [UserProfile]. Shared by the chat-member read
 * ([com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper]) and the
 * user_profiles cache read ([com.flipcash.app.persistence.sources.UserProfileDataSource]).
 */
fun UserProfileSerialized.toDomain(): UserProfile = UserProfile(
    displayName = displayName.orEmpty(),
    socialAccounts = socialAccounts.map { it.toDomain() },
    phoneNumber = phoneNumber,
    email = email,
    profilePicture = profilePicture,
)

fun SocialAccountSerialized.toDomain(): SocialAccount = when (this) {
    is SocialAccountSerialized.TwitterX -> SocialAccount.TwitterX(
        id = id,
        username = username,
        name = name,
        description = description,
        profilePicUrl = profilePicUrl,
        verifiedType = verifiedType?.let { name ->
            SocialAccount.TwitterX.VerifiedType.entries.firstOrNull { it.name == name }
        },
        followerCount = followerCount,
    )
}
