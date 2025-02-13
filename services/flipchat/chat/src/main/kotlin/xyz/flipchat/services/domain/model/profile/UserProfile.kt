package xyz.flipchat.services.domain.model.profile

import com.getcode.model.social.user.SocialProfile

data class UserProfile(
    val displayName: String,
    val socialProfiles: List<SocialProfile>
)
