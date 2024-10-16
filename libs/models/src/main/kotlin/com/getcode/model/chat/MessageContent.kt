package com.getcode.model.chat

import com.getcode.model.EncryptedData
import com.getcode.model.GenericAmount
import com.getcode.model.ID
import kotlinx.serialization.Serializable

@Serializable
sealed interface MessageContent {
    val kind: Int
    val isFromSelf: Boolean

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
    }

    @Serializable
    data class Exchange(
        val amount: GenericAmount,
        val verb: Verb,
        val reference: Reference,
        val hasInteracted: Boolean,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 2

        override fun hashCode(): Int {
            var result = amount.hashCode()
            result += verb.hashCode()
            result += reference.hashCode()
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
    }

    @Serializable
    data class ThankYou(
        val tipIntentId: ID,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 4

        override fun hashCode(): Int {
            var result = tipIntentId.hashCode()
            result += isFromSelf.hashCode()
            result += kind.hashCode()

            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ThankYou

            if (tipIntentId != other.tipIntentId) return false
            if (isFromSelf != other.isFromSelf) return false
            if (kind != other.kind) return false

            return true
        }
    }

    @Serializable
    data class IdentityRevealed(
        val memberId: ID,
        val identity: Identity,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 5

        override fun hashCode(): Int {
            var result = memberId.hashCode()
            result += identity.hashCode()
            result += isFromSelf.hashCode()
            result += kind.hashCode()

            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as IdentityRevealed

            if (memberId != other.memberId) return false
            if (identity != other.identity) return false
            if (isFromSelf != other.isFromSelf) return false
            if (kind != other.kind) return false

            return true
        }
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
    }

    companion object
}