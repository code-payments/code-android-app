package com.getcode.opencode.model.core

import com.getcode.crypt.Sha256Hash
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.solana.utils.DataSlice.byteToUnsignedInt
import com.getcode.opencode.internal.solana.utils.DataSlice.suffix
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.utils.deriveRendezvousKey
import com.kik.scan.Scanner
import com.getcode.utils.byteArrayToLong
import com.getcode.utils.encodeBase64
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

    val username: String?
        get() = (value as? Username)?.value

    val codeData: ByteArray
        get() = Scanner.encode(encode().toByteArray()) ?: ByteArray(LENGTH)

    fun encode(): List<Byte> {
        return when (value) {
            is Fiat -> encode(kind, value, nonce)
            is Username -> encode(kind, value)
            else -> throw IllegalArgumentException("Unsupported payload value: $value")
        }
    }

    private fun encode(kind: PayloadKind, fiat: Fiat, nonce: List<Byte>): List<Byte> {
        val data = MutableList<Byte>(LENGTH) { 0 }

        data[0] = kind.value.toByte()

        data[1] = fiat.currencyCode.ordinal.toByte()

        fiat.quarks.longToByteArray().forEachIndexed { index, byte ->
            data[index + OFFSET_QUARKS] = byte
        }

        nonce.toByteArray().forEachIndexed { index, byte ->
            data[index + OFFSET_NONCE] = byte
        }

        return data
    }

    private fun encode(kind: PayloadKind, username: Username): List<Byte> {
        val data = MutableList<Byte>(LENGTH) { 0 }
        data[0] = kind.value.toByte()

        val usernameString = username.value.take(USERNAME_LENGTH)

        // The username that uniquely represents a user's tip code. Cannot be longer than 15
        // bytes. Any additional space is represented by the base64-encoded SHA256 hash of the
        // username delimited by a period.
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
        const val OFFSET_QUARKS = 2
        const val OFFSET_USERNAME = 5
        const val OFFSET_NONCE = 10

        val Empty by lazy { OpenCodePayload(PayloadKind.Unknown, Fiat.Zero) }

        fun fromList(list: List<Byte>): OpenCodePayload {
            val kind = PayloadKind.entries.find { it.value == list[0].toInt() } ?: PayloadKind.Cash

            val value: PayloadValue = when (kind) {
                PayloadKind.Unknown -> Fiat.Zero
                PayloadKind.Cash,
                PayloadKind.MultiMintCash -> {
                    // grab currency
                    val currencyIndex = list[1].byteToUnsignedInt()
                    val currency = CurrencyCode.entries.toList()[currencyIndex]

                    val quarks = list.subList(2, OFFSET_NONCE).toByteArray().byteArrayToLong()
                    Fiat(currencyCode = currency, quarks = quarks)
                }

                PayloadKind.Tip -> {
                    val usernameBytes = list.suffix(OFFSET_USERNAME)
                    val usernameWithHash = String(usernameBytes.toByteArray())
                    val username = usernameWithHash.substringBeforeLast(".")
                    Username(username)
                }
            }

            // Tip codes carry a username in place of the nonce; only amount payloads have one.
            val nonce = if (kind == PayloadKind.Tip) emptyList() else list.suffix(OFFSET_NONCE)

            return OpenCodePayload(kind, value, nonce)
        }
    }
}



enum class PayloadKind(val value: Int) {
    Unknown(-1),
    Cash(0),
    MultiMintCash(1),
    Tip(2),
//    Login(3),
//    RequestPaymentV2(4),
}

/*

 Layout 0: Single token supported Cash (USDC)

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

 Layout 1: Multi-token supported Cash

 Same as layout 0.

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
 bytes. Any additional space is padded with the base64-encoded SHA256 hash of the
 username, delimited by a period.
*/