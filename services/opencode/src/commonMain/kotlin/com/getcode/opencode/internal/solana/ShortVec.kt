package com.getcode.opencode.internal.solana

import com.getcode.opencode.internal.solana.utils.DataSlice.tail
import java.io.ByteArrayInputStream

internal object ShortVec {
    /**
     * decodeLen decodes a ShortVec encoded length from the [input].
     *
     * @param input - the input stream that the length is encoded in
     * @return - returns the decoded length of the ShortVec and Offset
     */
    private fun decodeLen(input: ByteArrayInputStream): Pair<Int, Int> {
        var offset = 0
        val valBuf = ByteArray(1)
        var value = 0

        while (true) {
            input.read(valBuf)

            value = value or (valBuf[0].toInt() and 0x7f shl (offset * 7))
            offset++

            if ((valBuf[0].toInt() and 0x80) == 0) {
                break
            }
        }

        return Pair(value, offset)
    }

    /**
     * decodeLen decodes a ShortVec encoded length from the [input].
     *
     * @param input - the input list that the length is encoded in
     * @return - returns the decoded length of the ShortVec and Offset
     */
    fun decodeLen(input: List<Byte>): Pair<Int, List<Byte>> {
        val l = decodeLen(input.toByteArray().inputStream())
        return Pair(l.first, input.tail(l.second))
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
