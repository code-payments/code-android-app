package com.getcode.opencode.model.core

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.solana.utils.DataSlice.byteToUnsignedInt
import com.getcode.opencode.internal.solana.utils.DataSlice.suffix
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.utils.deriveRendezvousKey
import com.kik.scan.Scanner
import com.getcode.utils.byteArrayToLong
import com.getcode.utils.longToByteArray

data class OpenCodePayload(
    val kind: PayloadKind,
    val value: PayloadValue,
    val nonce: List<Byte> = emptyList(),
) {
    val rendezvous: KeyPair

    init {
        rendezvous = deriveRendezvousKey(encode().toByteArray())
    }

    val fiat: Fiat?
        get() = value as? Fiat

    val userId: ID?
        get() = (value as? UserId)?.value

    val codeData: ByteArray
        get() = Scanner.encode(encode().toByteArray()) ?: ByteArray(LENGTH)

    /** Serializes this payload into the fixed [LENGTH]-byte scan frame. */
    fun encode(): List<Byte> = kind.encode(value, nonce)

    companion object {
        const val LENGTH = 20
        const val OFFSET_QUARKS = 2
        const val OFFSET_NONCE = 10
        const val OFFSET_USER_ID = 1
        const val USER_ID_LENGTH = 16

        val Empty by lazy { OpenCodePayload(PayloadKind.Unknown, Fiat.Zero) }

        /** Parses a scan frame back into a payload, dispatching on the leading kind byte. */
        fun fromList(list: List<Byte>): OpenCodePayload {
            // `Scanner.decode` drops trailing zero bytes, so a frame that ends in zeros (e.g. a
            // user id with trailing zeros) comes back short. Restore the fixed frame first.
            val frame = if (list.size < LENGTH) {
                list + List(LENGTH - list.size) { 0.toByte() }
            } else {
                list
            }

            val kind = PayloadKind.from(frame[0].toInt())

            // `decode` returns null for a frame this kind can't parse — e.g. a foreign or corrupt
            // Kik code whose currency byte is out of range (Bugsnag 6a5a4523:
            // IndexOutOfBoundsException on CurrencyCode.entries[232], a 171-entry enum). Treat it
            // as Empty so the scanner ignores it instead of crashing or attempting a bogus grab.
            val value = kind.decode(frame) ?: return Empty
            return OpenCodePayload(kind, value, kind.decodeNonce(frame))
        }
    }
}


/*

 Layout 0: Single token supported Cash (USDC)

   0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19
 +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 | T | C |        Fiat                   |               Nonce                   |
 +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+

 (T) Type (1 byte)  — the scan code kind (see PayloadKind).
 (C) Currency Code (1 byte) — index into CurrencyCode.
 Fiat Amount (8 bytes) — quarks as a 64-bit unsigned integer.
 Nonce (10 bytes) — regenerated each time a new payment is initiated.

 Layout 1: Multi-token supported Cash — same as layout 0.

 Layout 2: Tip

   0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19
 +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 | T |                     User ID (16 bytes)                     |  reserved (0) |
 +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+

 (T) Type (1 byte) — PayloadKind.Tip (2).
 User ID (16 bytes) — the recipient's user id (a UUID) so others can tip them. Raw bytes,
   no rendezvous keypair. Matches the iOS TipCode.Payload frame.
*/
