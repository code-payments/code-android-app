package com.getcode.model

import android.webkit.MimeTypeMap
import com.codeinc.gen.user.v1.IdentityService
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.getcode.utils.serializer.PublicKeyAsStringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class TwitterUser(
    override val username: String,
    @Serializable(with = PublicKeyAsStringSerializer::class)
    override val tipAddress: PublicKey,
    override val imageUrl: String?,
    val displayName: String,
    val followerCount: Int,
    val verificationStatus: VerificationStatus,
    val costOfFriendship: Fiat,
    val isFriend: Boolean,
): TipMetadata {

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
                costOfFriendship = Fiat(currency = CurrencyCode.USD, amount = 1.00),
                isFriend = proto.isFriend
            )
        }
    }
}