package com.getcode.model.chat

import com.getcode.model.ID

data class Sender(
    val id: ID? = null,
    val profileImage: String? = null,
    val displayName: String? = null,
    val isHost: Boolean = false,
    val isSelf: Boolean = false,
    val isFullMember: Boolean = false,
    val isBlocked: Boolean = false,
)

data class Deleter(
    val id: ID? = null,
    val isHost: Boolean = false,
    val isSelf: Boolean = false,
)

data class MinimalMember(
    val id: ID? = null,
    private val profileImageUrl: String? = null,
    val displayName: String? = null,
    val isHost: Boolean = false,
    val isSelf: Boolean = false,
    val canSpeak: Boolean = false,
) {
    val imageData: Any? = profileImageUrl.nullIfEmpty() ?: id
}

private fun String?.nullIfEmpty() = if (this?.isEmpty() == true) null else this
