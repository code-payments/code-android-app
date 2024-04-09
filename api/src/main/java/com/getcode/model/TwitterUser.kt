package com.getcode.model

import android.webkit.MimeTypeMap
import com.codeinc.gen.user.v1.IdentityService
import com.codeinc.gen.user.v1.IdentityService.GetTwitterUserResponse
import com.getcode.solana.keys.PublicKey

data class TwitterUser(
    val username: String,
    val displayName: String,
    val imageUrl: String,
    val followerCount: Int,
    val tipAddress: PublicKey,
    val verificationStatus: VerificationStatus
) {

    val imageUrlSanitized: String
        get() {
            val url = imageUrl
            val extension = MimeTypeMap.getFileExtensionFromUrl(url)
            val urlWithoutExtension = url.removeSuffix(extension)
            val urlWithoutType = urlWithoutExtension.substringBeforeLast("_")

            return urlWithoutType.plus(".$extension")
        }

    enum class VerificationStatus {
        none, blue, government, unknown
    }

    companion object {
        fun invoke(proto: IdentityService.TwitterUser): TwitterUser? {
            val avatarUrl = proto.profilePicUrl

            val tipAddress = runCatching { PublicKey.fromByteString(proto.tipAddress.value) }.getOrNull() ?: return null

            return TwitterUser(
                username = proto.username,
                displayName = proto.name,
                imageUrl = avatarUrl,
                followerCount = proto.followerCount,
                tipAddress = tipAddress,
                verificationStatus = VerificationStatus.entries.getOrNull(proto.verifiedTypeValue) ?: VerificationStatus.unknown
            )
        }
    }
}