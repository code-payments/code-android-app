package com.getcode.utils

import kotlin.math.min

object DataSlice {
    data class ByteListConsume(val consumed: List<Byte>, val remaining: List<Byte>)

    fun List<Byte>.consume(length: Int): ByteListConsume {
        if (length <= 0) return ByteListConsume(listOf(), this)
        val consumed = this.prefix(length)
        val remaining = this.suffix(min(length, size))
        return ByteListConsume(consumed.toList(), remaining.toList())
    }

    fun List<Byte>.prefix(toIndex: Int): List<Byte> {
        if (toIndex < 0 || toIndex > size) return listOf()
        return this.subList(0, toIndex)
    }

    fun List<Byte>.suffix(fromIndex: Int): List<Byte> {
        if (fromIndex < 0 || fromIndex > size) return listOf()
        return this.subList(fromIndex, this.size)
    }

    fun List<Byte>.tail(fromIndex: Int): List<Byte> {
        return suffix(fromIndex)
    }
    fun <T>List<Byte>.chunk(size: Int, count: Int, block: (List<Byte>) -> T): List<T>? {
        val requestSize = size * count
        if (requestSize > this.size) return null

        val container = mutableListOf<T>()

        for (i in 0 until count) {
            val index = i * size
            val slice = this.subList(index, index + size)
            container.add(block(slice))
        }

        return container
    }

    fun Byte.byteToUnsignedInt() = if (this < 0) this + 256 else this
}