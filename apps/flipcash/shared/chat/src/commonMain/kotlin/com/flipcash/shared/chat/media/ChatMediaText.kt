package com.flipcash.shared.chat.media

/**
 * The words a photo message goes by where there is no room to show it: a reply's citation, the
 * conversation list, a notification. Pure so the vectors in `chat_media.json` can pin them; the
 * localized word for "Photo" is the caller's to pass.
 */
object ChatMediaText {

    /** Leads every one-line preview of a photo. An emoji, so not a string resource. */
    const val PREVIEW_PREFIX = "📷 "

    /** The caption as written, or [photoLabel] when there is none. */
    fun snippet(caption: String?, photoLabel: String): String =
        caption?.takeIf { it.isNotBlank() } ?: photoLabel

    /** [snippet] behind [PREVIEW_PREFIX]: what the conversation list and a quote's one-liner show. */
    fun preview(caption: String?, photoLabel: String): String =
        PREVIEW_PREFIX + snippet(caption, photoLabel)

    /**
     * A push for a photo: the caption, else the preview of the bare photo. English, because a push
     * body is built where no resources are at hand; the server's own body is localized if it ships one.
     */
    fun pushText(caption: String?): String =
        caption?.takeIf { it.isNotBlank() } ?: preview(null, "Photo")
}
