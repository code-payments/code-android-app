package com.flipcash.services.models

import android.os.Parcelable
import com.flipcash.services.models.chat.MediaItem
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
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
    // The ID of the user this profile belongs to. Server-provided on any fetched
    // profile; null for a locally-constructed one.
    val userId: ID? = null,
    // The user's public Flipcash handle. Public, so it is present for any user —
    // null when they haven't claimed one yet.
    val username: String? = null,
    // The minimum fee another user must pay to initialize a DM chat with this
    // user. Public, so it is present for any user, not just the caller. Null
    // when the user hasn't set one, in which case the server default applies.
    // Update it with ProfileController.setMinDmChatInitFee.
    val minDmChatInitFee: Fiat? = null,
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
            userId = null,
            username = null,
            minDmChatInitFee = null,
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
