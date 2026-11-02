package com.flipcash.shared.chat.link

/**
 * A preview the composer built for a link in the text it is about to send.
 *
 * [imageUploadId] is the [ChatMediaUploads][com.flipcash.shared.chat.media.ChatMediaUploads] chip
 * holding the page's image, staged when the preview loaded; null when the page has no image.
 */
data class OutgoingLinkPreview(
    val url: String,
    val title: String,
    val description: String = "",
    val imageUploadId: String? = null,
)
