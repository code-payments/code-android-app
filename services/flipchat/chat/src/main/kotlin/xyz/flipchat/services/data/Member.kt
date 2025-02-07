package xyz.flipchat.services.data

import com.getcode.model.ID
import com.getcode.model.chat.Pointer
import kotlinx.serialization.Serializable
import xyz.flipchat.services.user.social.SocialProfile

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