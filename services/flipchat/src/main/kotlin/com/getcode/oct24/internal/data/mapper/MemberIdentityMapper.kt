package com.getcode.oct24.internal.data.mapper

import com.codeinc.flipchat.gen.chat.v1.FlipchatService
import com.getcode.oct24.data.MemberIdentity
import com.getcode.services.mapper.Mapper
import javax.inject.Inject

class MemberIdentityMapper @Inject constructor(): Mapper<FlipchatService.MemberIdentity, MemberIdentity> {
    override fun map(from: FlipchatService.MemberIdentity): MemberIdentity {
        return MemberIdentity(
            displayName = from.displayName,
            imageUrl = from.profilePicUrl
        )
    }
}