package com.getcode.model.chat

import com.getcode.model.EncryptedData
import com.getcode.model.GenericAmount
import com.getcode.utils.base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed interface MessageContent {
    val kind: Int
    val isFromSelf: Boolean

    val content: String

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
        val hasInteracted: Boolean,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 2

        override fun hashCode(): Int {
            var result = amount.hashCode()
            result += verb.hashCode()
            result += (reference?.hashCode() ?: 0)
            result += hasInteracted.hashCode()
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
            if (hasInteracted != other.hasInteracted) return false
            if (isFromSelf != other.isFromSelf) return false
            if (kind != other.kind) return false

            return true
        }

        override val content: String = Json.encodeToString(this)
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

        override val content: String = Json.encodeToString(this)
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

    companion object {
        fun fromData(type: Int, content: String, isFromSelf: Boolean): MessageContent {
            return when (type) {
                0 -> Localized(content, isFromSelf)
                1 -> RawText(content, isFromSelf)
                2 -> Json.decodeFromString(content)
                3 -> Json.decodeFromString(content)
                4 -> Announcement(content, isFromSelf)
                6 -> Decrypted(content, isFromSelf)
                else -> throw IllegalArgumentException()
            }
        }
    }
}