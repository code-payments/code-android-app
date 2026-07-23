package com.getcode.opencode.model.core

import com.getcode.opencode.internal.solana.utils.DataSlice.byteToUnsignedInt
import com.getcode.opencode.internal.solana.utils.DataSlice.suffix
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.utils.byteArrayToLong
import com.getcode.utils.longToByteArray

/**
 * The kind of scan code — and its own codec. Each kind owns how it serializes its
 * [PayloadValue] into, and reads it back out of, the fixed [OpenCodePayload.LENGTH]-byte frame.
 * The leading [value] byte lets a single scanner dispatch frames by kind.
 */
sealed interface PayloadKind {
    /** The leading kind byte, written at offset 0 of the frame. */
    val value: Int

    /** Encodes [value] (and [nonce], where applicable) into the fixed-length scan frame. */
    fun encode(value: PayloadValue, nonce: List<Byte>): List<Byte>

    /**
     * Reads this kind's [PayloadValue] out of a (zero-padded) scan [frame], or null if the frame
     * can't be parsed as this kind (e.g. a cash frame whose currency byte is out of range).
     */
    fun decode(frame: List<Byte>): PayloadValue?

    /** Reads the nonce out of a scan [frame]; empty for kinds that carry none. */
    fun decodeNonce(frame: List<Byte>): List<Byte>

    /**
     * Cash-family kinds share the fiat frame (currency + quarks + nonce) and differ only in the
     * leading kind byte.
     */
    sealed class Payment(override val value: Int) : PayloadKind {
        override fun encode(value: PayloadValue, nonce: List<Byte>): List<Byte> {
            val data = MutableList<Byte>(OpenCodePayload.LENGTH) { 0 }
            data[0] = this.value.toByte()

            val fiat = value as Fiat
            data[1] = fiat.currencyCode.ordinal.toByte()
            fiat.quarks.longToByteArray().forEachIndexed { index, byte ->
                data[index + OpenCodePayload.OFFSET_QUARKS] = byte
            }
            nonce.toByteArray().forEachIndexed { index, byte ->
                data[index + OpenCodePayload.OFFSET_NONCE] = byte
            }
            return data
        }

        override fun decode(frame: List<Byte>): PayloadValue? {
            // Byte 1 is an index into CurrencyCode. A foreign or corrupt Kik code can carry a byte
            // outside that range; such a frame isn't a valid cash code, so fail decoding rather
            // than throw (Bugsnag 6a5a4523: IndexOutOfBoundsException on a 171-entry enum).
            val currency = CurrencyCode.entries.getOrNull(frame[1].byteToUnsignedInt()) ?: return null
            val quarks = frame.subList(OpenCodePayload.OFFSET_QUARKS, OpenCodePayload.OFFSET_NONCE)
                .toByteArray().byteArrayToLong()
            return Fiat(currencyCode = currency, quarks = quarks)
        }

        override fun decodeNonce(frame: List<Byte>): List<Byte> =
            frame.suffix(OpenCodePayload.OFFSET_NONCE)
    }

    /** Single-token cash (USDC). */
    data object Cash : Payment(0)

    /** Multi-token cash / gift card. */
    data object MultiMintCash : Payment(1)

    /**
     * A profile "tip code": the recipient's [UserId] (a 16-byte UUID) written raw at offset 1,
     * with the trailing bytes reserved. Carries no nonce and derives no rendezvous — matches the
     * iOS `TipCode.Payload` frame.
     */
    data object Tip : PayloadKind {
        override val value: Int = 2

        override fun encode(value: PayloadValue, nonce: List<Byte>): List<Byte> {
            val data = MutableList<Byte>(OpenCodePayload.LENGTH) { 0 }
            data[0] = this.value.toByte()

            val userId = (value as UserId).value
            userId.take(OpenCodePayload.USER_ID_LENGTH).forEachIndexed { index, byte ->
                data[index + OpenCodePayload.OFFSET_USER_ID] = byte
            }
            return data
        }

        override fun decode(frame: List<Byte>): PayloadValue {
            val start = OpenCodePayload.OFFSET_USER_ID
            return UserId(frame.subList(start, start + OpenCodePayload.USER_ID_LENGTH).toList())
        }

        override fun decodeNonce(frame: List<Byte>): List<Byte> = emptyList()
    }

    /**
     * An unrecognized kind. Encodes as a zeroed cash frame (used by [OpenCodePayload.Empty]) and
     * decodes to a zero amount.
     */
    data object Unknown : Payment(-1) {
        override fun decode(frame: List<Byte>): PayloadValue = Fiat.Zero
        override fun decodeNonce(frame: List<Byte>): List<Byte> = emptyList()
    }

    companion object {
        /** The kind for a leading [value] byte, defaulting to [Cash] for unrecognized bytes. */
        fun from(value: Int): PayloadKind = when (value) {
            Cash.value -> Cash
            MultiMintCash.value -> MultiMintCash
            Tip.value -> Tip
            Unknown.value -> Unknown
            else -> Cash
        }
    }
}
