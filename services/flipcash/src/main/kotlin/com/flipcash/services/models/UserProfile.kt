package com.flipcash.services.models

data class UserProfile(
    val displayName: String?,
    val socialAccounts: List<SocialAccount>,
    val phoneNumber: VerifiableContactMethod?,
    val email: VerifiableContactMethod?,
) {
    /** The phone number only when it has been verified — backwards-compatible accessor. */
    val verifiedPhoneNumber: String? get() = phoneNumber?.takeIf { it.verified }?.value

    /** The email address only when it has been verified — backwards-compatible accessor. */
    val verifiedEmailAddress: String? get() = email?.takeIf { it.verified }?.value
}

sealed interface SocialAccount {
    val id: String
    data class TwitterX(
        override val id: String,
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
