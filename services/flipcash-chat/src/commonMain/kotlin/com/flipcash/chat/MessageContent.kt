package com.flipcash.chat

/**
 * One item of a chat message: `messaging.v1.Content`.
 *
 * Mirrors Android's `com.flipcash.services.models.chat.MessageContent` for the types a client sends
 * or a bot reads. Media, link previews and system events are not ported yet: Media decodes to
 * [Unsupported], a link preview is dropped from [Text], and [System] keeps only its fallback text.
 *
 * Ids are raw bytes. `ByteArray` has identity equality, so these classes compare their arrays by
 * content.
 */
sealed interface MessageContent {

    class Text(val text: String) : MessageContent {
        override fun equals(other: Any?) = other is Text && text == other.text
        override fun hashCode() = text.hashCode()
        override fun toString() = "Text(${text.length} chars)"
    }

    /**
     * A payment posted into the chat. The client sends only [intentId], [quarks], [mint], [verb],
     * the receivers and [paymentRequestId]; the server fills [currency] and [nativeAmount] in
     * what it relays.
     */
    class Cash(
        val intentId: ByteArray,
        val mint: ByteArray,
        val quarks: Long,
        val currency: String = "",
        val nativeAmount: Double = 0.0,
        val verb: Verb = Verb.SENT,
        val receiver: ByteArray? = null,
        val multiReceiver: List<Payment> = emptyList(),
        val paymentRequestId: ByteArray? = null,
        val paymentReturn: PaymentReturn? = null,
    ) : MessageContent {
        enum class Verb { SENT, TIPPED }

        class Payment(val receiver: ByteArray, val nativeAmount: Double, val quarks: Long) {
            override fun equals(other: Any?) = other is Payment && receiver.contentEquals(other.receiver) &&
                nativeAmount == other.nativeAmount && quarks == other.quarks
            override fun hashCode() = receiver.contentHashCode() * 31 + quarks.hashCode()
        }

        /** [offer] marks a cash message that offers a return; [returnOf] is the message a return answers. */
        class PaymentReturn(val offer: Boolean, val returnOf: Long?) {
            override fun equals(other: Any?) = other is PaymentReturn && offer == other.offer && returnOf == other.returnOf
            override fun hashCode() = offer.hashCode() * 31 + returnOf.hashCode()
        }

        override fun equals(other: Any?) = other is Cash &&
            intentId.contentEquals(other.intentId) && mint.contentEquals(other.mint) && quarks == other.quarks &&
            currency == other.currency && nativeAmount == other.nativeAmount && verb == other.verb &&
            receiver.contentEqualsNullable(other.receiver) && multiReceiver == other.multiReceiver &&
            paymentRequestId.contentEqualsNullable(other.paymentRequestId) && paymentReturn == other.paymentReturn

        override fun hashCode() = intentId.contentHashCode() * 31 + quarks.hashCode()
        override fun toString() = "Cash($verb, $quarks quarks)"
    }

    data class Reply(val repliedMessageId: Long, val content: List<MessageContent>) : MessageContent

    sealed interface Widget : MessageContent {
        class PaymentRequest(val paymentRequestId: ByteArray) : Widget {
            override fun equals(other: Any?) = other is PaymentRequest && paymentRequestId.contentEquals(other.paymentRequestId)
            override fun hashCode() = paymentRequestId.contentHashCode()
        }

        data class ShareProfile(val username: String) : Widget

        /** A widget variant this build does not know. */
        data object Unsupported : Widget
    }

    /** Server-authored. [userJoined] is set for a join event; other events show [fallbackText]. */
    class System(val fallbackText: String, val userJoined: UserJoined? = null) : MessageContent {
        override fun equals(other: Any?) = other is System && fallbackText == other.fallbackText && userJoined == other.userJoined
        override fun hashCode() = fallbackText.hashCode()
        override fun toString() = "System($fallbackText, $userJoined)"

        /** At most one of [admittedBy] and [invitedBy] is set; neither means a direct join. */
        class UserJoined(val userId: ByteArray, val admittedBy: ByteArray? = null, val invitedBy: ByteArray? = null) {
            override fun equals(other: Any?) = other is UserJoined && userId.contentEquals(other.userId) &&
                admittedBy.contentEqualsNullable(other.admittedBy) && invitedBy.contentEqualsNullable(other.invitedBy)
            override fun hashCode() = userId.contentHashCode()
            override fun toString() = "UserJoined(admitted=${admittedBy != null}, invited=${invitedBy != null})"
        }
    }

    class Deleted(val deletedAtEpochSeconds: Long, val deletedAtNanos: Int, val deletedBy: ByteArray?) : MessageContent {
        override fun equals(other: Any?) = other is Deleted && deletedAtEpochSeconds == other.deletedAtEpochSeconds &&
            deletedAtNanos == other.deletedAtNanos && deletedBy.contentEqualsNullable(other.deletedBy)
        override fun hashCode() = deletedAtEpochSeconds.hashCode()
    }

    /**
     * Content still sealed. [scheme] is the raw `EncryptedContent.Scheme` number, kept even when this
     * build does not know it, so the caller can tell "unsupported scheme" from "failed to open".
     */
    class Encrypted(val scheme: Int, val nonce: ByteArray, val ciphertext: ByteArray) : MessageContent {
        override fun equals(other: Any?) = other is Encrypted && scheme == other.scheme &&
            nonce.contentEquals(other.nonce) && ciphertext.contentEquals(other.ciphertext)
        override fun hashCode() = nonce.contentHashCode()
        override fun toString() = "Encrypted(scheme=$scheme)"

        companion object {
            /** `EncryptedContent.Scheme.X25519_XCHACHA20POLY1305`: a DM. */
            const val SCHEME_DM = 1

            /** `EncryptedContent.Scheme.CHAT_KEY_XCHACHA20POLY1305`: a private group. */
            const val SCHEME_GROUP = 2
        }
    }

    /** A content type this build does not decode (including Media, not yet ported). Decode-only. */
    data object Unsupported : MessageContent
}

internal fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    if (this == null) other == null else other != null && contentEquals(other)
