package com.flipcash.services.models

import com.flipcash.services.models.chat.MediaItem

data class UserProfile(
    val displayName: String?,
    val socialAccounts: List<SocialAccount>,
    val phoneNumber: VerifiableContactMethod?,
    val email: VerifiableContactMethod?,
    // The user's profile picture, as the renditions it is stored as (DISPLAY for
    // the profile view, THUMBNAIL for avatars). Null when unset.
    val profilePicture: MediaItem? = null,
) {
    /** The phone number only when it has been verified — backwards-compatible accessor. */
    val verifiedPhoneNumber: String? get() = phoneNumber?.takeIf { it.verified }?.value

    /** The email address only when it has been verified — backwards-compatible accessor. */
    val verifiedEmailAddress: String? get() = email?.takeIf { it.verified }?.value

    companion object {
        val Empty = UserProfile(
            displayName = null,
            socialAccounts = emptyList(),
            phoneNumber = null,
            email = null,
        )
    }
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
