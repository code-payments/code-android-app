package com.getcode.model.chat

import com.getcode.model.EncryptedData
import com.getcode.model.GenericAmount
import com.getcode.model.ID
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed interface MessageContent {
    val kind: Int
    val isFromSelf: Boolean

    val content: String

    data class Unknown(
        override val isFromSelf: Boolean,
    ): MessageContent {
        override val kind: Int = -1
        override val content: String
            get() = "unknown"
    }

    @Serializable
    data class Localized(
        val value: String,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 0

        override fun hashCode(): Int {
            var result = value.hashCode()
            result += isFromSelf.hashCode()
            result += kind.hashCode()

            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Localized

            if (value != other.value) return false
            if (isFromSelf != other.isFromSelf) return false
            if (kind != other.kind) return false

            return true
        }

        override val content: String = value
    }

    @Serializable
    data class RawText(
        val value: String,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 1

        override fun hashCode(): Int {
            var result = value.hashCode()
            result += isFromSelf.hashCode()
            result += kind.hashCode()

            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as RawText

            if (value != other.value) return false
            if (isFromSelf != other.isFromSelf) return false
            if (kind != other.kind) return false

            return true
        }

        override val content: String = value
    }

    @Serializable
    data class Exchange(
        val amount: GenericAmount,
        val verb: Verb,
        val reference: Reference?,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 2

        override fun hashCode(): Int {
            var result = amount.hashCode()
            result += verb.hashCode()
            result += (reference?.hashCode() ?: 0)
            result += isFromSelf.hashCode()
            result += kind.hashCode()

            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Exchange

            if (amount != other.amount) return false
            if (verb != other.verb) return false
            if (reference != other.reference) return false
            if (isFromSelf != other.isFromSelf) return false
            if (kind != other.kind) return false

            return true
        }

        @Serializable
        internal data class Content(
            val amount: GenericAmount,
            val verb: Verb,
            val reference: Reference?,
        )

        @Transient
        override val content: String = Json.encodeToString(Content(amount, verb, reference))
    }

    @Serializable
    data class SodiumBox(
        val data: EncryptedData,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 3

        override fun hashCode(): Int {
            var result = data.hashCode()
            result += isFromSelf.hashCode()
            result += kind.hashCode()

            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SodiumBox

            if (data != other.data) return false
            if (isFromSelf != other.isFromSelf) return false
            if (kind != other.kind) return false

            return true
        }

        @Serializable
        internal data class Content(
            val data: EncryptedData,
        )

        @Transient
        override val content: String = Json.encodeToString(Content(data))
    }

    @Serializable
    data class Announcement(
        val value: String,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 4

        override fun hashCode(): Int {
            var result = value.hashCode()
            result += isFromSelf.hashCode()
            result += kind.hashCode()

            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Announcement

            if (value != other.value) return false
            if (isFromSelf != other.isFromSelf) return false
            if (kind != other.kind) return false

            return true
        }

        override val content: String = value
    }

    @Serializable
    data class Decrypted(
        val data: String,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 6

        override fun hashCode(): Int {
            var result = data.hashCode()
            result += isFromSelf.hashCode()
            result += kind.hashCode()

            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Decrypted

            if (data != other.data) return false
            if (isFromSelf != other.isFromSelf) return false
            if (kind != other.kind) return false

            return true
        }

        override val content: String = data
    }

    @Serializable
    data class Reaction(
        val emoji: String,
        val originalMessageId: ID,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 7

        override fun hashCode(): Int {
            var result = emoji.hashCode()
            result += originalMessageId.hashCode()
            result += isFromSelf.hashCode()
            result += kind.hashCode()

            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Reaction

            if (emoji != other.emoji) return false
            if (originalMessageId != other.originalMessageId) return false
            if (isFromSelf != other.isFromSelf) return false
            if (kind != other.kind) return false

            return true
        }

        @Serializable
        internal data class Content(
            val emoji: String,
            val originalMessageId: ID,
        )

        @Transient
        override val content: String = Json.encodeToString(Content(emoji, originalMessageId))
    }

    @Serializable
    data class Reply(
        val text: String,
        val originalMessageId: ID,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 8

        override fun hashCode(): Int {
            var result = text.hashCode()
            result += originalMessageId.hashCode()
            result += isFromSelf.hashCode()
            result += kind.hashCode()

            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Reply

            if (text != other.text) return false
            if (originalMessageId != other.originalMessageId) return false
            if (isFromSelf != other.isFromSelf) return false
            if (kind != other.kind) return false

            return true
        }

        @Serializable
        internal data class Content(
            val text: String,
            val originalMessageId: ID,
        )

        @Transient
        override val content: String = Json.encodeToString(Content(text, originalMessageId))
    }

    @Serializable
    data class DeletedMessage(
        val originalMessageId: ID,
        val messageDeleter: ID,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 9

        override fun hashCode(): Int {
            var result = originalMessageId.hashCode()
            result += messageDeleter.hashCode()
            result += isFromSelf.hashCode()
            result += kind.hashCode()

            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DeletedMessage

            if (originalMessageId != other.originalMessageId) return false
            if (messageDeleter != other.messageDeleter) return false
            if (isFromSelf != other.isFromSelf) return false
            if (kind != other.kind) return false

            return true
        }

        @Serializable
        internal data class Content(
            val originalMessageId: ID,
            val messageDeleter: ID,
        )

        @Transient
        override val content: String = Json.encodeToString(Content(originalMessageId, messageDeleter))
    }

    companion object {
        fun fromData(type: Int, content: String, isFromSelf: Boolean): MessageContent? {
            return when (type) {
                0 -> Localized(content, isFromSelf)
                1 -> RawText(content, isFromSelf)
                2 -> {
                    val data = Json.decodeFromString<Exchange.Content>(content)
                    Exchange(data.amount, data.verb, data.reference, isFromSelf)
                }
                3 -> {
                    val data = Json.decodeFromString<SodiumBox.Content>(content)
                    SodiumBox(data.data, isFromSelf)
                }
                4 -> Announcement(content, isFromSelf)
                6 -> Decrypted(content, isFromSelf)
                7 -> {
                    val data = Json.decodeFromString<Reaction.Content>(content)
                    Reaction(data.emoji, data.originalMessageId, isFromSelf)
                }
                8 -> {
                    val data = Json.decodeFromString<Reply.Content>(content)
                    Reply(data.text, data.originalMessageId, isFromSelf)
                }
                9 -> {
                    val data = Json.decodeFromString<DeletedMessage.Content>(content)
                    DeletedMessage(data.originalMessageId, data.messageDeleter, isFromSelf)
                }
                else -> Unknown(isFromSelf)
            }
        }
    }
}