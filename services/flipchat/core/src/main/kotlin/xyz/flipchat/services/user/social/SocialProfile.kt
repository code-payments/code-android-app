package xyz.flipchat.services.user.social

sealed interface SocialProfile {
    data object Unknown: SocialProfile

    data class X(
        val id: String,
        val username: String,
        val friendlyName: String,
        val description: String,
        val profilePicUrl: String?,
        val verificationType: VerificationType,
        val followerCount: Int,
    ): SocialProfile {
        enum class VerificationType {
            NONE, // 0
            BLUE, // 1
            BUSINESS, // 2
            GOVERNMENT, // 3
            UNKNOWN
        }
    }
}
