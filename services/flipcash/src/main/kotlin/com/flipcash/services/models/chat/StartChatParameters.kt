package com.flipcash.services.models.chat

/**
 * Parameters for starting a new chat. The variant selects the kind of chat created.
 *
 * Only group chats are creatable through StartChat today; DM chats are opened by resolving a
 * [ChatId] instead and come into existence on the server the first time a message is sent.
 */
sealed interface StartChatParameters {
    /** Parameters for creating a group chat. */
    data class Group(
        /** Title for the chat. */
        val title: String,
        /** The blob holding the ORIGINAL picture the caller uploaded. Optional. */
        val picture: BlobId? = null,
        /**
         * Participation requirements for the chat. Optional; if not set, the chat has no
         * participation requirements. The caller must satisfy the rules for the chat to start.
         */
        val rules: ChatRules? = null,
    ) : StartChatParameters
}
