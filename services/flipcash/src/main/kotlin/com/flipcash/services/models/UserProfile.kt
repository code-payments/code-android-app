package com.flipcash.services.models

data class UserProfile(
    val displayName: String?,
    val socialAccounts: List<SocialAccount>,
    val verifiedPhoneNumber: String?,
    val verifiedEmailAddress: String?,
)

sealed interface SocialAccount {
    data class TwitterX(
        val id: String,
        val username: String,
        val name: String,
        val description: String,
        val profilePicUrl: String,
        val verifiedType: VerifiedType?,
        val followerCount: Int,
    ): SocialAccount {
        enum class VerifiedType {
            NONE,
            BLUE,
            BUSINESS,
            GOVERNMENT,
        }
    }
}
