package com.getcode.oct24.data.mapper

import com.codeinc.flipchat.gen.chat.v1.ChatService
import com.getcode.oct24.data.MemberIdentity
import com.getcode.services.mapper.Mapper
import javax.inject.Inject

class MemberIdentityMapper @Inject constructor(): Mapper<ChatService.MemberIdentity, MemberIdentity> {
    override fun map(from: ChatService.MemberIdentity): MemberIdentity {
        return MemberIdentity(
            displayName = from.displayName,
            imageUrl = from.profilePicUrl
        )
    }
}