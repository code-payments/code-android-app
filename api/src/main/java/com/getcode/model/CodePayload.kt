package com.getcode.model

import org.kin.sdk.base.tools.byteArrayToLong
import org.kin.sdk.base.tools.longToByteArray

data class CodePayload(
    val kind: Kind,
    val kin: Kin,
    val nonce: List<Byte>
) {
    fun encode(): List<Byte> {
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
            val kind = Kind.values().find { it.value == list[0].toInt() } ?: Kind.Cash
            val quarks = list.subList(OFFSET_QUARKS, OFFSET_NONCE).toByteArray().byteArrayToLong()
            val nonce = list.subList(OFFSET_NONCE, LENGTH)

            return CodePayload(kind, Kin(quarks), nonce)
        }
    }
}

enum class Kind(val value: Int) {
    Cash(0),
    GiftCard(1)
}

/*

   0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19
 +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 | T |            Amount             |                   Nonce                   |
 +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+

 Type (T) (1 byte)

 The first byte of the data in all Code scan codes is reserved for the scan
 code type. This field indicates which type of scan code data is contained
 in the scan code. The expected format for each type is outlined below.

 Amount (8 bytes)

 This field indicates the number of quarks the payment is for. It should be
 represented as a 64-bit unsigned integer.

 Nonce (11 bytes)

 This field is an 11-byte randomly-generated nonce. It should be regenerated
 each time a new payment is initiated.

 */
