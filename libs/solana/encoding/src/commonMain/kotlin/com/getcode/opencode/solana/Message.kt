package com.getcode.opencode.solana

import com.getcode.opencode.internal.solana.model.MessageAddressLookupTable
import com.getcode.utils.DataSlice.byteToUnsignedInt
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import kotlin.math.abs

internal const val messageVersionSerializationOffset = 127

enum class MessageVersion {
    legacy,
    v0,
    ;
}

/**
 * Represents a Solana transaction message, abstracting over different message versions.
 *
 * Solana transactions can carry different types of messages:
 * - [Legacy]: The original message format.
 * - [VersionedV0]: The newer Versioned Transaction format (V0), which supports Address Lookup Tables.
 *
 * This interface provides a unified API to access common fields like [accountKeys], [recentBlockhash],
 * and [instructions] regardless of the underlying format.
 */
sealed interface Message {
    data class Legacy(val message: LegacyMessage): Message
    data class VersionedV0(val message: VersionedMessageV0): Message

    val description: String
        get() = when (this) {
            is Legacy -> "LegacyMessage"
            is VersionedV0 -> "V0"
        }

    val version: MessageVersion
        get() = when (this) {
            is Legacy -> MessageVersion.legacy
            is VersionedV0 -> MessageVersion.v0
        }

    val header: MessageHeader
        get() = when (this) {
            is Legacy -> message.header
            is VersionedV0 -> message.header
        }

    val accountKeys: List<PublicKey>
        get() = when (this) {
            is Legacy -> message.accounts.map { it.publicKey }
            is VersionedV0 -> message.staticAccountKeys
        }

    var recentBlockhash: Hash
        get() = when (this) {
            is Legacy -> message.recentBlockhash
            is VersionedV0 -> message.recentBlockhash
        }

        set(value) {
            when (this) {
                is Legacy -> message.recentBlockhash = value
                is VersionedV0 -> message.recentBlockhash = value
            }
        }

    val instructions: List<CompiledInstruction>
        get() = when (this) {
            // `accountKeys` above is this message's own full account list (see
            // `LegacyMessage.newInstance`), built from the same `message.instructions` being
            // compiled here, so every program/account an instruction references is always
            // present in it — `compile` returning `null` here would mean this message's own
            // invariant was violated elsewhere.
            is Legacy -> message.instructions.map { instruction ->
                instruction.compile(accountKeys)
                    ?: error("instruction references an account missing from this message")
            }
            is VersionedV0 -> message.instructions
        }

    val versionDescription: String
        get() = when (this) {
            is Legacy -> "Legacy"
            is VersionedV0 -> "V0"
        }

    val addressLookupTables: List<MessageAddressLookupTable>
        get() = when (this) {
            is Legacy -> emptyList()
            is VersionedV0 -> message.addressLookupTables
        }

    fun encode(): List<Byte> {
        return when (this) {
            is Legacy -> message.encode().toList()
            is VersionedV0 -> message.encode()
        }
    }

    companion object {
        fun newInstance(data: List<Byte>): Message? {
            if (data.isEmpty()) return null
            val firstByte = data.first().byteToUnsignedInt()
            val version = if (firstByte < messageVersionSerializationOffset) {
                MessageVersion.legacy
            } else if (firstByte == (MessageVersion.v0.ordinal + messageVersionSerializationOffset)) {
                MessageVersion.v0
            } else {
                return null
            }

            return when (version) {
                MessageVersion.legacy -> {
                    val message = LegacyMessage.newInstance(data) ?: return null
                    Legacy(message)
                }
                MessageVersion.v0 -> {
                    val message = VersionedMessageV0.newInstance(data) ?: return null
                    VersionedV0(message)
                }
            }
        }
    }
}