package com.flipcash.shared.chat.media

/**
 * What a send with attachments turns into, decided before anything is uploaded or stored.
 *
 * The rules are iOS's (code-payments/code-ios-app#959) and the vectors in `chat_media.json` pin
 * them: with no chips the text is an ordinary message; with chips there is one media message per
 * chip, in chip order, the caption on the last so it lands under the last image, the reply on the
 * first. Text is never a message of its own once a chip exists.
 */
object ChatMediaSendPlan {

    /** The composer's ceiling on staged photos. */
    const val MAX_ATTACHMENTS = 10

    sealed interface Message {
        data class Text(val text: String, val replyTo: Long?) : Message

        /** [chip] is whatever id the caller staged the photo under. */
        data class Media(val chip: String, val caption: String?, val replyTo: Long?) : Message
    }

    fun build(chips: List<String>, text: String, replyTo: Long?): List<Message> {
        val body = text.trim()
        if (chips.isEmpty()) {
            return if (body.isEmpty()) emptyList() else listOf(Message.Text(body, replyTo))
        }
        return chips.mapIndexed { index, chip ->
            Message.Media(
                chip = chip,
                caption = body.takeIf { index == chips.lastIndex && it.isNotEmpty() },
                replyTo = replyTo.takeIf { index == 0 },
            )
        }
    }
}
