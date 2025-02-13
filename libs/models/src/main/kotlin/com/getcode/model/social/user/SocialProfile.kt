package com.getcode.model.social.user

import android.webkit.MimeTypeMap
import com.getcode.model.chat.LinkedSocialProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface SocialProfile {
    data object Unknown : SocialProfile

    data class X(
        val id: String,
        val username: String,
        val friendlyName: String,
        val description: String,
        private val _profilePicUrl: String?,
        val verificationType: VerificationType,
        val followerCount: Int,
    ) : SocialProfile {
        enum class VerificationType {
            NONE, // 0
            BLUE, // 1
            BUSINESS, // 2
            GOVERNMENT, // 3
            UNKNOWN
        }

        val followerCountFormatted: String = when {
            followerCount > 1000 -> {
                val decimal = followerCount.toDouble() / 1_000
                val formattedString = String.format("%.1fK", decimal)
                formattedString
            }

            else -> followerCount.toString()
        }

        val profilePicUrl: String?
            get() {
                val url = _profilePicUrl ?: return null
                val extension = MimeTypeMap.getFileExtensionFromUrl(url)
                val urlWithoutExtension = url.removeSuffix(extension)
                val urlWithoutType = urlWithoutExtension.substringBeforeLast("_")

                return urlWithoutType.plus(".$extension")
            }
    }

    fun toLinked(): LinkedSocialProfile? {
        return when (this) {
            Unknown -> null
            is X -> {
                val metadata = XExtraData(
                    friendlyName = friendlyName,
                    description = description,
                    verificationType = verificationType,
                    followerCount = followerCount
                )
                LinkedSocialProfile(
                    platformType = "x",
                    username = username,
                    profileImageUrl = profilePicUrl,
                    rawMetadata = Json.encodeToString(metadata)
                )
            }
        }
    }
}

interface SocialMetadata

@Serializable
data class XExtraData(
    val friendlyName: String,
    val description: String,
    val verificationType: SocialProfile.X.VerificationType,
    val followerCount: Int,
): SocialMetadata