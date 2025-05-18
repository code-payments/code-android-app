package com.getcode.opencode.model.core

import com.getcode.codeScanner.CodeScanner
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.solana.utils.DataSlice.byteToUnsignedInt
import com.getcode.opencode.internal.solana.utils.DataSlice.suffix
import com.getcode.opencode.internal.solana.utils.DataSlice.toLong
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.utils.deriveRendezvousKey
import org.kin.sdk.base.tools.byteArrayToLong
import org.kin.sdk.base.tools.longToByteArray
import java.nio.ByteBuffer

data class OpenCodePayload(
    val kind: PayloadKind,
    val value: Fiat,
    val nonce: List<Byte> = emptyList(),
) {
    val rendezvous: KeyPair

    init {
        rendezvous = deriveRendezvousKey(encode(kind = kind, fiat = value, nonce = nonce).toByteArray())
    }

    val fiat: Fiat?
        get() {
            return value as? Fiat ?: return null
        }

    val codeData: ByteArray
        get() = CodeScanner.encode(encode().toByteArray())

    fun encode(): List<Byte> {
        return encode(kind, value, nonce)
    }

    private fun encode(kind: PayloadKind, fiat: Fiat, nonce: List<Byte>): List<Byte> {
        val data = MutableList<Byte>(LENGTH) { 0 }

        data[0] = kind.value.toByte()

        data[1] = fiat.currencyCode.ordinal.toByte()

        fiat.quarks.toLong().longToByteArray().forEachIndexed { index, byte ->
            data[index + OFFSET_QUARKS] = byte
        }

        nonce.toByteArray().forEachIndexed { index, byte ->
            data[index + OFFSET_NONCE] = byte
        }

        return data
    }

    companion object {
        const val LENGTH = 20
        const val OFFSET_QUARKS = 2
        const val OFFSET_NONCE = 10

        val Empty = OpenCodePayload(PayloadKind.Unknown, Fiat.Zero)

        fun fromList(list: List<Byte>): OpenCodePayload {
            val kind = PayloadKind.entries.find { it.value == list[0].toInt() } ?: PayloadKind.Cash

            val value = when (kind) {
                PayloadKind.Unknown -> Fiat.Zero
                PayloadKind.Cash -> {
                    // grab currency
                    val currencyIndex = list[1].byteToUnsignedInt()
                    val currency = CurrencyCode.entries.toList()[currencyIndex]

                    val quarks = list.subList(2, OFFSET_NONCE).toByteArray().byteArrayToLong()
                    Fiat(currencyCode = currency, quarks = quarks.toULong())
                }
            }

            val nonce = list.suffix(OFFSET_NONCE)

            return OpenCodePayload(kind, value, nonce)
        }
    }
}



enum class PayloadKind(val value: Int) {
    Unknown(-1),
    Cash(0),
//    GiftCard(1),
//    RequestPayment(2),
//    Login(3),
//    RequestPaymentV2(4),
}

/*

 Layout 0: Cash

   0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19
 +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 | T | C |        Fiat                   |               Nonce                   |
 +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+

 (T) Type (1 byte)

 The first byte of the data in all Code scan codes is reserved for the scan
 code type. This field indicates which type of scan code data is contained
 in the scan code. The expected format for each type is outlined below.

 (C) Currency Code (1 bytes)

 This field indicates the currency code for the fiat amount. The value is an
 encoded index less than 255 that maps to a currency code in CurrencyCode

 Fiat Amount (8 bytes)

 This field indicates the number of quarks the payment is for. It should be
 represented as a 64-bit unsigned integer.

 Nonce (10 bytes)

 This field is an 11-byte randomly-generated nonce. It should be regenerated
 each time a new payment is initiated.

 Layout 1: Gift Card

 Same as layout 0.

 Layout 2: Payment Request

 Same as layout 0.
*/