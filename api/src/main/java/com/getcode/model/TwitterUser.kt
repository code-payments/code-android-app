package com.getcode.model

import android.webkit.MimeTypeMap
import com.codeinc.gen.user.v1.IdentityService.GetTwitterUserResponse
import com.getcode.solana.keys.PublicKey

data class TwitterUser(
    val username: String,
    val imageUrl: String,
    val followerCount: Int,
    val tipAddress: PublicKey,
) {

    val imageUrlSanitized: String
        get() {
            val url = imageUrl
            val extension = MimeTypeMap.getFileExtensionFromUrl(url)
            val urlWithoutExtension = url.removeSuffix(extension)
            val urlWithoutType = urlWithoutExtension.substringBeforeLast("_")

            return urlWithoutType.plus(".$extension")
        }

    companion object {
        fun invoke(proto: GetTwitterUserResponse): TwitterUser? {
            val avatarUrl = proto.profilePicUrl
            val avatarBytes = proto.profilePicUrlBytes

            val tipAddress = runCatching { PublicKey.fromByteString(proto.tipAddress.value) }.getOrNull() ?: return null

            return TwitterUser(
                username = proto.name,
                imageUrl = avatarUrl,
                followerCount = proto.followerCount,
                tipAddress = tipAddress
            )
        }
    }
}