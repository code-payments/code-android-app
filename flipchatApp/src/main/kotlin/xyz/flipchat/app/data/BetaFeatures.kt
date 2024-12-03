package xyz.flipchat.app.data

data class BetaFeatures(
    val replyToMessage: Boolean,
    val jumpToBottom: Boolean
) {
    companion object {
        val Default = BetaFeatures(
            replyToMessage = false,
            jumpToBottom = false
        )
    }
}
