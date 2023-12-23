package com.getcode.model

import com.getcode.codeScanner.CodeScanner
import com.getcode.utils.DataSlice.byteToUnsignedInt
import com.getcode.utils.DataSlice.suffix
import com.getcode.utils.DataSlice.toLong
import org.kin.sdk.base.tools.byteArrayToLong
import org.kin.sdk.base.tools.longToByteArray
import timber.log.Timber
import java.nio.ByteBuffer

data class CodePayload(
    val kind: Kind,
    val value: Value,
    val nonce: List<Byte>,
) {

    val kin: Kin?
        get() {
            return value as? Kin ?: return null
        }

    val fiat: Fiat?
        get() {
            return value as? Fiat ?: return null
        }

    val codeData: ByteArray
        get() = CodeScanner.encode(encode().toByteArray())

    fun encode(): List<Byte> {
        return when (value) {
            is Kin -> encode(kind, value, nonce)
            is Fiat -> encode(kind, value, nonce)
            else -> {
                Timber.e("Attempting to encode an unknown value ${value.javaClass.simpleName}")
                emptyList()
            }
        }
    }

    private fun encode(kind: Kind, fiat: Fiat, nonce: List<Byte>): List<Byte> {
        val data = MutableList<Byte>(LENGTH) { 0 }

        val amount = (fiat.amount * 100).toLong()

        data[0] = kind.value.toByte()

        data[1] = fiat.currency.ordinal.toByte()

        amount.longToByteArray().forEachIndexed { index, byte ->
            data[index + 2] = byte
        }

        nonce.toByteArray().forEachIndexed { index, byte ->
            data[index + OFFSET_NONCE] = byte
        }

        return data
    }

    private fun encode(kind: Kind, kin: Kin, nonce: List<Byte>): List<Byte> {
        val data = MutableList<Byte>(LENGTH) { 0 }
        data[0] = kind.value.toByte()

        kin.quarks.longToByteArray().forEachIndexed { index, byte ->
            data[index + OFFSET_QUARKS] = byte
        }

        nonce.toByteArray().forEachIndexed { index, byte ->
            data[index + OFFSET_NONCE] = byte
        }

        return data
    }

    companion object {
        const val LENGTH = 20
        const val OFFSET_QUARKS = 1
        const val OFFSET_NONCE = 9

        fun fromList(list: List<Byte>): CodePayload {
            val kind = Kind.entries.find { it.value == list[0].toInt() } ?: Kind.Cash

            val (value, nonce) = when (kind) {
                Kind.Cash,
                Kind.GiftCard -> {
                    val quarks = list.subList(1, OFFSET_NONCE).toByteArray().byteArrayToLong()
                    val nonce = list.suffix(OFFSET_NONCE)

                    Kin(quarks) to nonce
                }

                Kind.RequestPayment -> {
                    // grab currency
                    val currencyIndex = list[1].byteToUnsignedInt()
                    val currency = CurrencyCode.entries.toList()[currencyIndex]

                    // grab the fiat value
                    val amountData = ByteArray(7)
                    val buffer = ByteBuffer.wrap(list.toByteArray())
                    buffer.position(2)
                    buffer.get(amountData, 0, 7)
                    val amountCents = amountData.toLong()

                    val fiat = Fiat(currency = currency, amount = amountCents / 100.0)

                    // grab nonce
                    val nonce = list.suffix(OFFSET_NONCE)
                    fiat to nonce
                }
            }

            return CodePayload(kind, value, nonce)
        }
    }
}



enum class Kind(val value: Int) {
    Cash(0),
    GiftCard(1),
    RequestPayment(2),
}

/*

 Layout 0: Cash

   0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19
 +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 | T |            Amount             |                   Nonce                   |
 +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+

 (T) Type (1 byte)

 The first byte of the data in all Code scan codes is reserved for the scan
 code type. This field indicates which type of scan code data is contained
 in the scan code. The expected format for each type is outlined below.

 Kin Amount in Quarks (8 bytes)

 This field indicates the number of quarks the payment is for. It should be
 represented as a 64-bit unsigned integer.

 Nonce (11 bytes)

 This field is an 11-byte randomly-generated nonce. It should be regenerated
 each time a new payment is initiated.

 Layout 1: Gift Card

 Same as layout 0.

 Layout 2: Payment Request

   0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19
 +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 | T | C |        Fiat               |                   Nonce                   |
 +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+

 (T) Type (1 byte)

 The first byte of the data in all Code scan codes is reserved for the scan
 code type. This field indicates which type of scan code data is contained
 in the scan code. The expected format for each type is outlined below.

 (C) Currency Code (1 bytes)

 This field indicates the currency code for the fiat amount. The value is an
 encoded index less than 255 that maps to a currency code in CurrencyCode.swift

 Fiat Amount (7 bytes)

 This field indicates the fiat amount, denominated in `Currency` above. The amount
 is an integer value calculated as follows: $5.00 x 100 = 500. The decimals are
 offset by multiplying by 100 and encoding the integer result. When decoding, the
 amount should be divided by 100 again to return the original value.

 Nonce (11 bytes)

 This field is an 11-byte randomly-generated nonce. It should be regenerated
 each time a new payment is initiated.

 */