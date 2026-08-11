package com.flipcash.services.models

import android.os.Parcelable
import com.flipcash.services.models.chat.MediaItem
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Parcelize
@Serializable
data class UserProfile(
    val displayName: String,
    val socialAccounts: List<SocialAccount>,
    val phoneNumber: VerifiableContactMethod?,
    val email: VerifiableContactMethod?,
    // The user's profile picture, as the renditions it is stored as (DISPLAY for
    // the profile view, THUMBNAIL for avatars). Null when unset.
    val profilePicture: MediaItem? = null,
    // When the user joined Flipcash (server-provided). Null when unknown.
    val joinedAt: Instant? = null,
    // The hex color for the user's tip card customization. Null when unset.
    val tipCardColor: String? = null,
): Parcelable {
    /** The phone number only when it has been verified — backwards-compatible accessor. */
    val verifiedPhoneNumber: String? get() = phoneNumber?.takeIf { it.verified }?.value

    /** The email address only when it has been verified — backwards-compatible accessor. */
    val verifiedEmailAddress: String? get() = email?.takeIf { it.verified }?.value

    companion object {
        val Empty = UserProfile(
            displayName = "",
            socialAccounts = emptyList(),
            phoneNumber = null,
            email = null,
            tipCardColor = null,
        )
    }
}

@Parcelize
sealed interface SocialAccount: Parcelable {
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
