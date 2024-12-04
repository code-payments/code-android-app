package xyz.flipchat.app.data

data class BetaFeatures(
    val replyToMessage: Boolean,
    val jumpToBottom: Boolean,
    val joinAsSpectator: Boolean,
) {
    companion object {
        val Default = BetaFeatures(
            replyToMessage = false,
            jumpToBottom = false,
            joinAsSpectator = false
        )
    }
}
