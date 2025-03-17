package com.getcode.model.chat

import com.getcode.model.ID
import com.getcode.model.social.user.XExtraData
import kotlinx.serialization.json.Json

data class Sender(
    val id: ID? = null,
    private val profileImageUrl: String? = null,
    private val name: String? = null,
    val isHost: Boolean = false,
    val isSelf: Boolean = false,
    val isFullMember: Boolean = false,
    val isBlocked: Boolean = false,
    val socialProfiles: List<LinkedSocialProfile> = emptyList(),
) {
    val displayName: String?
        get() {
            val social = socialProfiles.firstOrNull() ?: return name
            return when (social.platformType) {
                "x" -> {
                    val metadata = social.metadata<XExtraData>()
                    metadata?.friendlyName ?: name
                }

                else -> name
            }
        }

    val profileImage: String? = socialProfiles.firstOrNull()?.let {
        when (it.platformType) {
            "x" -> it.profileImageUrl
            else -> profileImageUrl.nullIfEmpty()
        }
    } ?: profileImageUrl.nullIfEmpty()
}

data class Deleter(
    val id: ID? = null,
    val isHost: Boolean = false,
    val isSelf: Boolean = false,
)

data class MinimalMember(
    val id: ID? = null,
    private val profileImageUrl: String? = null,
    private val name: String? = null,
    val isHost: Boolean = false,
    val isSelf: Boolean = false,
    val canSpeak: Boolean = false,
    val socialProfiles: List<LinkedSocialProfile> = emptyList(),
) {
    val displayName: String?
        get() {
            val social = socialProfiles.firstOrNull() ?: return name
            return when (social.platformType) {
                "x" -> {
                    val metadata = social.metadata<XExtraData>()
                    metadata?.friendlyName ?: name
                }

                else -> name
            }
        }

    val imageData: Any? = socialProfiles.firstOrNull()?.let {
        when (it.platformType) {
            "x" -> it.profileImageUrl
            else -> profileImageUrl.nullIfEmpty() ?: id
        }
    } ?: profileImageUrl.nullIfEmpty() ?: id
}

data class LinkedSocialProfile(
    val platformType: String,
    val username: String,
    val profileImageUrl: String?,
    val isVerifiedOnPlatform: Boolean,
    val rawMetadata: String?,
) {
    inline fun <reified M> metadata(): M? = runCatching {
        Json.decodeFromString<M>(rawMetadata.orEmpty())
    }.getOrNull()
}

private fun String?.nullIfEmpty() = if (this?.isEmpty() == true) null else this
