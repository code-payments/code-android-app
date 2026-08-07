package com.getcode.vendor

/**
 * Base58 encoder/decoder — the Solana/Bitcoin alphabet.
 *
 * Ported from the bitcoinj implementation; adapted to pure Kotlin (no java.util.Arrays,
 * no BigInteger) so it compiles on all Kotlin Multiplatform targets.
 *
 * JVM-only helpers (encodeChecked, decodeChecked, hashTwice, decodeToBigInteger) live in
 * `androidMain`; they use MessageDigest + BigInteger which are not available on Kotlin/Native.
 *
 * Copyright 2011 Google Inc. / 2018 Andreas Schildbach — Apache 2.0 License.
 */
object Base58 {

    private val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()
    private val ENCODED_ZERO = ALPHABET[0]
    private val INDEXES = IntArray(128) { -1 }.also { arr ->
        ALPHABET.forEachIndexed { i, c -> arr[c.code] = i }
    }

    sealed class AddressFormatException(message: String?) : IllegalArgumentException(message) {
        class InvalidCharacter(val character: Char, val position: Int) :
            AddressFormatException("Invalid character '${character}' at position $position")
        class InvalidDataLength(message: String?) : AddressFormatException(message)
        class InvalidChecksum : AddressFormatException("Checksum does not validate")
    }

    /**
     * Encodes the given bytes as a base58 string (no checksum appended).
     *
     * @param input the bytes to encode
     * @return the base58-encoded string
     */
    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""

        // Working copy so we can modify in-place during divmod.
        val data = input.copyOf()

        // Count leading zeros.
        var zeros = 0
        while (zeros < data.size && data[zeros] == 0.toByte()) zeros++

        val encoded = CharArray(data.size * 2)
        var outputStart = encoded.size
        var inputStart = zeros
        while (inputStart < data.size) {
            encoded[--outputStart] = ALPHABET[divmod(data, inputStart, 256, 58).toInt()]
            if (data[inputStart] == 0.toByte()) inputStart++
        }

        // Strip leading ENCODED_ZERO chars that are spurious, then re-add exactly `zeros` of them.
        while (outputStart < encoded.size && encoded[outputStart] == ENCODED_ZERO) outputStart++
        repeat(zeros) { encoded[--outputStart] = ENCODED_ZERO }

        return encoded.concatToString(outputStart, encoded.size)
    }

    /**
     * Decodes the given base58 string into the original data bytes.
     *
     * @param input the base58-encoded string to decode
     * @return the decoded data bytes
     * @throws AddressFormatException.InvalidCharacter if a character is not in the alphabet
     */
    @Throws(AddressFormatException::class)
    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)

        val input58 = ByteArray(input.length)
        for (i in input.indices) {
            val c = input[i]
            val digit = if (c.code < 128) INDEXES[c.code] else -1
            if (digit < 0) throw AddressFormatException.InvalidCharacter(c, i)
            input58[i] = digit.toByte()
        }

        var zeros = 0
        while (zeros < input58.size && input58[zeros] == 0.toByte()) zeros++

        val decoded = ByteArray(input.length)
        var outputStart = decoded.size
        var inputStart = zeros
        while (inputStart < input58.size) {
            decoded[--outputStart] = divmod(input58, inputStart, 58, 256)
            if (input58[inputStart] == 0.toByte()) inputStart++
        }

        while (outputStart < decoded.size && decoded[outputStart] == 0.toByte()) outputStart++

        return decoded.copyOfRange(outputStart - zeros, decoded.size)
    }

    /**
     * Long division on a byte array representing a big-endian number in the given [base].
     * Modifies [number] in place. Returns the remainder byte.
     */
    private fun divmod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Byte {
        var remainder = 0
        for (i in firstDigit until number.size) {
            val digit = number[i].toInt() and 0xFF
            val temp = remainder * base + digit
            number[i] = (temp / divisor).toByte()
            remainder = temp % divisor
        }
        return remainder.toByte()
    }
}
