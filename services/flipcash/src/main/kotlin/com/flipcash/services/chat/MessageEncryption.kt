package com.flipcash.services.chat

import com.flipcash.services.models.chat.MessageContent

/**
 * How an end-to-end encrypted message stands on this device. Null on a [ChatMessage] means the
 * message was sent in plaintext.
 *
 * [com.flipcash.services.models.chat.ChatMessage.content] follows it: the decrypted content when
 * [Decrypted], the raw [MessageContent.Encrypted] otherwise.
 */
sealed interface MessageEncryption {
    /**
     * Opened on this device, or sent from it. [sealed] is the ciphertext as it went over the wire,
     * kept so a later copy of the same message can be recognised without opening it again.
     */
    data class Decrypted(val sealed: MessageContent.Encrypted) : MessageEncryption

    /**
     * The chat key could not be derived because a key fetch failed. Not a decrypt failure: the
     * message is hidden and opened again later, rather than shown as undecryptable.
     */
    data object KeyPending : MessageEncryption

    data class Undecryptable(val reason: UndecryptableReason) : MessageEncryption
}

enum class UndecryptableReason {
    /**
     * An unknown scheme, or a plaintext type this client doesn't render. A newer client can read
     * it.
     */
    Unsupported,

    /** The ciphertext failed authentication on a scheme this client supports. */
    Authentication,
}

/** The outcome of opening one [MessageContent.Encrypted]. */
sealed interface OpenedContent {
    data class Plaintext(val content: MessageContent) : OpenedContent
    data object KeyPending : OpenedContent
    data class Undecryptable(val reason: UndecryptableReason) : OpenedContent
}
