package com.getcode.opencode.internal.solana

import com.getcode.utils.DataSlice.tail

internal object ShortVec {

    /**
     * Maximum number of continuation bytes [decodeLen] will read before giving up. A ShortVec
     * length is encoded 7 bits per byte, and [encodeLen] never emits more than 5 bytes for any
     * `Int` (5 * 7 = 35 bits comfortably covers all 31 magnitude bits of `Int.MAX_VALUE`), so no
     * well-formed input ever needs a 6th byte. Capping the read here rejects a lone dangling
     * continuation bit (or a deliberately long run of 0x80-flagged bytes) with `null` instead of
     * indexing past the end of [input], and keeps the accumulated `value` from being built from
     * more than 32 bits' worth of shifted-in bits.
     */
    private const val MAX_LEN_BYTES = 5

    /**
     * decodeLen decodes a ShortVec encoded length from the [input].
     *
     * @param input - the input list that the length is encoded in
     * @return - the decoded length of the ShortVec and the remaining bytes after it, or `null`
     *   if [input] is too short to contain a complete ShortVec length (including an empty list,
     *   or a final byte whose continuation bit is still set), or if the decoded value would be
     *   negative (a byte 5's low bits land in `Int`'s sign bit, so a crafted 5-byte sequence can
     *   otherwise produce a negative "length" — never valid, since a ShortVec length is a count).
     */
    fun decodeLen(input: List<Byte>): Pair<Int, List<Byte>>? {
        var offset = 0
        var value = 0

        while (true) {
            if (offset >= input.size || offset >= MAX_LEN_BYTES) return null
            val byte = input[offset]

            value = value or (byte.toInt() and 0x7f shl (offset * 7))
            offset++

            if ((byte.toInt() and 0x80) == 0) {
                break
            }
        }

        if (value < 0) return null

        return Pair(value, input.tail(offset))
    }


    /**
     * encodeLen ShortVec encodes [length].
     *
     * @param length - the length
     * @return - returns ShortVec encoded length
     */
    fun encodeLen(length: Int): List<Byte> {
        val data = mutableListOf<Byte>()
        var remaining = length

        while (true) {
            var byte = (remaining and 0x7f).toByte()
            remaining = remaining shr 7

            if (remaining == 0) {
                data.add(byte)
                return data
            }

            byte = (byte.toInt() or 0x80).toByte()
            data.add(byte)
        }
    }

    /**
     * encodeLen ShortVec encodes [list].
     *
     * @param list - the input list
     * @return - returns ShortVec encoded list
     */
    fun encodeList(list: List<List<Byte>>): List<Byte> {
        val container = encodeLen(list.size).toMutableList()
        list.forEach { container.addAll(it) }
        return container
    }

    /**
     * encodeLen ShortVec encodes [list].
     *
     * @param list - the input list
     * @return - returns ShortVec encoded list
     */
    fun encode(list: List<Byte>): List<Byte> {
        val container = encodeLen(list.size).toMutableList()
        container.addAll(list)
        return container
    }
}
