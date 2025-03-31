package com.getcode.model

import android.webkit.MimeTypeMap
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.serializer.PublicKeyAsStringSerializer
import kotlinx.serialization.Serializable

@Serializable
data class TwitterUser(
    override val username: String,
    @Serializable(with = PublicKeyAsStringSerializer::class)
    override val tipAddress: PublicKey,
    override val imageUrl: String?,
    val displayName: String,
    val followerCount: Int,
    val verificationStatus: VerificationStatus,
    override val costOfFriendship: Fiat,
    override val isFriend: Boolean,
    override val chatId: ID,
): SocialUser {

    override val platform: String = "X"

    override val imageUrlSanitized: String?
        get() {
            val url = imageUrl ?: return null
            val extension = MimeTypeMap.getFileExtensionFromUrl(url)
            val urlWithoutExtension = url.removeSuffix(extension)
            val urlWithoutType = urlWithoutExtension.substringBeforeLast("_")

            return urlWithoutType.plus(".$extension")
        }

    enum class VerificationStatus {
        none, blue, business, government, unknown
    }

    companion object
}