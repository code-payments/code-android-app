package com.getcode.keys

import com.getcode.solana.keys.*

typealias Seed16 = Key16

typealias Seed32     = Key32
typealias Hash       = Key32

typealias PrivateKey = Key64

class Signature(bytes: List<Byte>): Key64(bytes) {
    companion object {
        val zero = Signature(ByteArray(LENGTH_64).toList())
    }
}