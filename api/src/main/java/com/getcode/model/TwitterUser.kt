package com.getcode.model

import com.codeinc.gen.user.v1.IdentityService.GetTwitterUserResponse
import com.getcode.solana.keys.PublicKey

data class TwitterUser(
    val username: String,
    val avatarUrl: String,
    val avatarBytes: List<Byte>,
    val followerCount: Int,
    val tipAddress: PublicKey,
) {
    companion object {
        fun invoke(proto: GetTwitterUserResponse): TwitterUser? {
            val avatarUrl = proto.profilePicUrl
            val avatarBytes = proto.profilePicUrlBytes

            val tipAddress = runCatching { PublicKey.fromByteString(proto.tipAddress.value) }.getOrNull() ?: return null

            return TwitterUser(
                username = proto.name,
                avatarUrl = avatarUrl,
                avatarBytes = avatarBytes.toList(),
                followerCount = proto.followerCount,
                tipAddress = tipAddress
            )
        }
    }
}