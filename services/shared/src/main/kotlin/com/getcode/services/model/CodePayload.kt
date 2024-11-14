package com.getcode.services.model

import com.getcode.codeScanner.CodeScanner
import com.getcode.crypt.Sha256Hash
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.CurrencyCode
import com.getcode.model.Fiat
import com.getcode.model.Kin
import com.getcode.model.Value
import com.getcode.services.model.Username
import com.getcode.utils.DataSlice.byteToUnsignedInt
import com.getcode.utils.DataSlice.suffix
import com.getcode.utils.DataSlice.toLong
import com.getcode.services.utils.deriveRendezvousKey
import com.getcode.utils.encodeBase64
import org.kin.sdk.base.tools.byteArrayToLong
import org.kin.sdk.base.tools.longToByteArray
import java.nio.ByteBuffer

data class CodePayload(
    val kind: Kind,
    val value: Value,
    val nonce: List<Byte> = emptyList(),
) {
    val rendezvous: KeyPair

    init {
        rendezvous = when (value) {
            is Fiat -> deriveRendezvousKey(encode(kind = kind, fiat = value, nonce = nonce).toByteArray())
            is Kin -> deriveRendezvousKey(encode(kind = kind, kin = value, nonce = nonce).toByteArray())
            is Username -> deriveRendezvousKey(encode(kind = kind, username = value).toByteArray())
            else -> throw IllegalArgumentException()
        }
    }

    val kin: Kin?
        get() {
            return value as? Kin ?: return null
        }

    val fiat: Fiat?
        get() {
            return value as? Fiat ?: return null
        }

    val username: String?
        get() {
            return (value as? Username)?.value ?: return null
        }

    val codeData: ByteArray
        get() = CodeScanner.encode(encode().toByteArray())

    fun encode(): List<Byte> {
        return when (value) {
            is Kin -> encode(kind, value, nonce)
            is Fiat -> encode(kind, value, nonce)
            is Username -> encode(kind, value)
            else -> throw IllegalArgumentException()
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

    private fun encode(kind: Kind, username: Username): List<Byte> {
        val data = MutableList<Byte>(LENGTH) { 0 }
        data[0] = kind.value.toByte()

        val usernameString = username.value.take(USERNAME_LENGTH)

        // The username that uniquely represents a user's tip code. Cannot be longer than 15
        // bytes. Any additional space is represented by the base64 encoded SHA256 hash of the username
        // delimited by a period.
        val paddedUsername = usernameString.let {
            var padding = ""
            val paddingRequired = (USERNAME_LENGTH - it.length)
            if (paddingRequired > 0) {
                padding = "."
            }

            if (paddingRequired > 1) {
                val hash = Sha256Hash.hash(usernameString.toByteArray()).encodeBase64()
                padding += hash.take(paddingRequired - 1)
            }

            "$it$padding"
        }

        paddedUsername.toByteArray().forEachIndexed { index, byte ->
            data[index + OFFSET_USERNAME] = byte
        }

        return data
    }

    companion object {
        const val LENGTH = 20

        const val USERNAME_LENGTH = 15

        const val OFFSET_QUARKS = 1
        const val OFFSET_USERNAME = 5
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

                Kind.RequestPayment,
                Kind.RequestPaymentV2,
                Kind.Login -> {
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
                Kind.Tip -> {
                    val usernameBytes = list.suffix(OFFSET_USERNAME)
                    val usernameWithHash = String(usernameBytes.toByteArray())
                    val hash = usernameWithHash.substringAfterLast(".")
                    val username = usernameWithHash.substringBeforeLast(".")
                    Username(username) to emptyList()
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
    Login(3),
    RequestPaymentV2(4),
    Tip(5)
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

 Layout 5: Tip

   0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19
 +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 | T |     Flags     |             username                  | ... remainder (0) |
 +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+

 (T) Type (1 byte)

 The first byte of the data in all Code scan codes is reserved for the scan
 code type. This field indicates which type of scan code data is contained
 in the scan code.

 (F) Flags (4 bytes)

 Optional flags may provide additional context on the type of username embedded in
 the scan code.

 Username (15 bytes)

 The username that uniquely represents a user's tip code. Cannot be longer than 15
 bytes. Any additional space is represented by an empty string in (remainder).
*/