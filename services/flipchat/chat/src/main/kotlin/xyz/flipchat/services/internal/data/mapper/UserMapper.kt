package xyz.flipchat.services.internal.data.mapper

import com.getcode.services.mapper.Mapper
import com.getcode.utils.base58
import xyz.flipchat.services.data.Member
import xyz.flipchat.services.domain.model.people.FlipchatUser
import javax.inject.Inject

class UserMapper @Inject constructor(): Mapper<Member, FlipchatUser> {
    override fun map(from: Member): FlipchatUser {
        return FlipchatUser(
            userIdBase58 = from.id.base58,
            memberName = from.identity?.displayName,
            imageUri = from.identity?.imageUrl,
        )
    }
}