package com.getcode.model

import android.webkit.MimeTypeMap
import com.codeinc.gen.user.v1.IdentityService
import com.codeinc.gen.user.v1.friendshipCostOrNull
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
                verificationStatus = VerificationStatus.entries.getOrNull(proto.verifiedTypeValue) ?: VerificationStatus.unknown,
                costOfFriendship = proto.friendshipCostOrNull?.let {
                    val currency = CurrencyCode.tryValueOf(it.currency) ?: return@let null
                    Fiat(currency, it.nativeAmount)
                } ?: Fiat(currency = CurrencyCode.USD, amount = 1.00),
                isFriend = runCatching { proto.isFriend }.getOrNull() ?: false,
                chatId = proto.friendChatId.value.toList()
            )
        }
    }
}