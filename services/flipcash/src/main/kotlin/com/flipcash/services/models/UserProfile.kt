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
    // True when the current username was assigned by the server from the display
    // name rather than chosen with setUsername. Private: only set on the caller's
    // own profile; false for anyone else's and when there is no username. Choosing
    // a different username clears it.
    val isUsernameAutoAssigned: Boolean = false,
    // Free-text bio, up to 160 characters. Public. Empty when unset.
    val bio: String = "",
    // The user's cover picture, as the renditions it is stored as. Null when unset.
    val coverPicture: MediaItem? = null,
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
            isUsernameAutoAssigned = false,
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
