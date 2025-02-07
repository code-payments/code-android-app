package xyz.flipchat.services.domain.model.profile

import xyz.flipchat.services.user.social.SocialProfile

data class UserProfile(
    val displayName: String,
    val socialProfiles: List<SocialProfile>
)
