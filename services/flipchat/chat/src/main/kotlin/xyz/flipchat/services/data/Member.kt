package xyz.flipchat.services.data

import com.getcode.model.ID
import com.getcode.model.chat.Pointer
import com.getcode.model.social.user.SocialProfile
import kotlinx.serialization.Serializable

@Serializable
data class Member(
    val id: ID,
    val isSelf: Boolean,
    val isModerator: Boolean,
    val isMuted: Boolean,
    val isSpectator: Boolean,
    val identity: MemberIdentity?,
    val pointers: List<Pointer>,
)

@Serializable
data class MemberIdentity(
    val displayName: String,
    val imageUrl: String?,
    val socialProfiles: List<SocialProfile>,
)