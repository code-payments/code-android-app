package com.flipcash.shared

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/**
 * Bulk conversions between Foundation's `NSData` and Kotlin's `ByteArray`.
 *
 * Swift callers have no other way to hand a `ByteArray` its contents than `set(index:value:)`,
 * which is one Objective-C message send per byte. Copying through `NSData` instead moves the
 * whole buffer in a single `memcpy`, which matters for anything larger than a key or a hash —
 * the discrete-curve tables are 6.7 MB.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object SharedBytes {

    /** Copies [data]'s bytes into a new `ByteArray`. */
    fun byteArray(data: NSData): ByteArray {
        val size = data.length.toInt()
        if (size == 0) return ByteArray(0)
        val bytes = ByteArray(size)
        bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return bytes
    }

    /** Copies [bytes] into a new `NSData`. */
    fun data(bytes: ByteArray): NSData {
        if (bytes.isEmpty()) return NSData()
        return bytes.usePinned {
            NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong())
        }
    }
}
