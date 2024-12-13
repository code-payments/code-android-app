package xyz.flipchat.services.internal.data.mapper

import com.codeinc.flipchat.gen.chat.v1.ChatService
import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.data.MemberIdentity
import javax.inject.Inject

class MemberIdentityMapper @Inject constructor(): Mapper<ChatService.MemberIdentity, MemberIdentity> {
    override fun map(from: ChatService.MemberIdentity): MemberIdentity {
        return MemberIdentity(
            displayName = from.displayName,
            imageUrl = from.profilePicUrl
        )
    }
}