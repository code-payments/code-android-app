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
)
