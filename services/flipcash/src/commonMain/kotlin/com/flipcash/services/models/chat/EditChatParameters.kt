package com.flipcash.services.models.chat

/**
 * Parameters for `Chat.EditChat`. Every field is optional and independently no-op when unset —
 * an all-null instance is a valid, well-defined call that changes nothing. Group chats only.
 */
data class EditChatParameters(
    // New title, 1-64 characters, moderated the same way as StartChat's. Null leaves the
    // title unchanged.
    val title: String? = null,
    // The already-uploaded-and-READY blob to use as the new picture. The client uploads only
    // the ORIGINAL rendition; the server derives the rest. Null leaves the picture unchanged.
    val picture: BlobId? = null,
    // The already-uploaded-and-READY blob to use as the new cover picture. Null leaves the cover
    // unchanged.
    val coverPicture: BlobId? = null,
    // Null leaves the description unchanged; see [DescriptionEdit] for set vs clear.
    val description: DescriptionEdit? = null,
)

/**
 * An edit to a group's description, up to 160 characters, moderated the same way as the title.
 * Modeled as two cases rather than a nullable string because the wire distinguishes "leave it"
 * (wrapper unset) from "clear it" (wrapper set, empty value), and an empty string alone could not
 * carry both.
 */
sealed interface DescriptionEdit {
    /** Replaces the description with [value]. */
    data class Set(val value: String) : DescriptionEdit

    /** Removes the description. */
    data object Clear : DescriptionEdit
}
