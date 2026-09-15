package com.getcode.opencode.solana

import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.LENGTH_32
import com.getcode.solana.keys.LENGTH_64
import com.getcode.solana.keys.Signature

/**
 * Flat, `ByteArray`-only entry point for Solana message + transaction wire-format encode/decode,
 * meant to cross the Kotlin/Native Obj-C bridge into `SharedCoreKit` once `:kmp:shared-core`
 * exports this module.
 *
 * Two findings rule out exporting [Message]/[SolanaTransaction]/[Instruction]/[AddressLookupTable]
 * directly alongside this object. First, all four names already exist as Swift types in
 * `FlipcashCore/Sources/FlipcashCore/Solana/` — the next phase keeps those Swift types and
 * re-bodies them rather than replacing them, so exporting the Kotlin types under the same names
 * would collide with the types that survive. Second, [Message] is a `sealed interface`, which
 * crosses the Obj-C bridge as a protocol — a reference type with no exhaustive `switch` — while
 * Swift's `Message` is a value-type `enum`; `recentBlockhash` shows the cost, since the Swift
 * setter rebinds `self` with a copy while the Kotlin one mutates the underlying object in place.
 * That is a semantics change, not a rename.
 *
 * So every function here takes and returns `ByteArray` (never `List<Byte>`, which boxes per
 * element crossing the bridge), and the [Message]/[SolanaTransaction] hierarchy never appears in a
 * parameter or a return type — callers on the Swift side never hold a Kotlin `Message`.
 */
object SolanaEncoding {

    /**
     * Parses [bytes] as a Solana message — legacy or v0, decided by the version-prefix byte the
     * wire format already carries — and immediately re-serializes what it parsed. A non-null
     * result proves [bytes] is a well-formed message this codec round-trips byte-for-byte; `null`
     * means [bytes] did not parse as either version.
     */
    fun decodeMessage(bytes: ByteArray): ByteArray? =
        Message.newInstance(bytes.toList())?.encode()?.toByteArray()

    /**
     * Parses [bytes] as a message, replaces its `recentBlockhash` with [blockhash], and
     * re-serializes it. This is `Message.recentBlockhash`'s setter plus `encode()`, exposed as the
     * one message mutation a caller actually needs without ever holding a Kotlin `Message`:
     * refreshing the blockhash immediately before signing. Returns `null` if [bytes] does not
     * parse, or [blockhash] is not exactly 32 bytes.
     */
    fun encodeMessage(bytes: ByteArray, blockhash: ByteArray): ByteArray? {
        if (blockhash.size != LENGTH_32) return null
        val message = Message.newInstance(bytes.toList()) ?: return null
        message.recentBlockhash = Hash(blockhash.toList())
        return message.encode().toByteArray()
    }

    /**
     * Parses [bytes] as a full transaction (signature(s) + message) and immediately re-serializes
     * it. A non-null result proves [bytes] is a well-formed transaction this codec round-trips
     * byte-for-byte; `null` means [bytes] did not parse.
     */
    fun decodeTransaction(bytes: ByteArray): ByteArray? =
        SolanaTransaction.fromList(bytes.toList())?.encode()?.toByteArray()

    /**
     * Builds transaction wire bytes from an already-encoded [message] and [signatures] — each
     * signature 64 bytes, concatenated in signer order, as produced by the ed25519 signing this
     * module deliberately does not own (moved to `SolanaTransactionSigning.kt` in
     * `:services:opencode` in Phase 3, since `sign()` needs the JNI `Ed25519`, not this module's
     * dependencies). Validates [signatures]' length against the message's own required-signature
     * count. Returns `null` if [message] does not parse, [signatures] is not a multiple of 64
     * bytes, or the signature count does not match the message's header.
     */
    fun encodeTransaction(message: ByteArray, signatures: ByteArray): ByteArray? {
        if (signatures.size % LENGTH_64 != 0) return null
        val parsedMessage = Message.newInstance(message.toList()) ?: return null
        val signatureList = signatures.toList().chunked(LENGTH_64).map { Signature(it) }
        if (signatureList.size != parsedMessage.header.requiredSignatures) return null
        return SolanaTransaction(parsedMessage, signatureList).encode().toByteArray()
    }
}
