package com.getcode.chatcipher

/** Which chats send `EncryptedContent`. A product rule, separate from the [ChatCipher] scheme. */
object ChatEncryptionPolicy {
    private val FLIPCASH_USER_ID_BYTES = "70c4a3df54af439a88fea7de606d04cb".chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    /**
     * The raw 16-byte user id of the @flipcash account (UUID `70c4a3df-54af-439a-88fe-a7de606d04cb`).
     * DMs with it are not end-to-end encrypted for now: the backend sends its onboarding messages in
     * plaintext. Returns a copy.
     */
    val FLIPCASH_USER_ID: ByteArray get() = FLIPCASH_USER_ID_BYTES.copyOf()

    /**
     * Whether a message to [peerUserId] should be sent as `EncryptedContent`: the chat is a DM
     * (`CONTACT_DM` or `TIP_DM`), the chat's `use_e2ee` flag is set, and the peer is not @flipcash.
     *
     * [useE2ee] is a transitional server flag. After launch the rule drops it and becomes "a DM with
     * anyone but @flipcash"; this is the only place that reads it.
     *
     * [peerUserId] is the raw `UserId.value`, a 16-byte UUID. An id of any other length is treated
     * as not @flipcash rather than rejected.
     */
    fun shouldEncrypt(isDirectMessage: Boolean, useE2ee: Boolean, peerUserId: ByteArray): Boolean =
        isDirectMessage && useE2ee && !peerUserId.contentEquals(FLIPCASH_USER_ID_BYTES)
}
