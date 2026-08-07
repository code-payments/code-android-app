package com.getcode.crypt

import java.io.File
import java.math.BigInteger
import java.security.MessageDigest

/** Returns a new SHA-256 [MessageDigest] instance (JVM/Android only). */
fun Sha256Hash.Companion.newDigest(): MessageDigest =
    MessageDigest.getInstance("SHA-256")

/**
 * Returns the wrapped bytes interpreted as a positive (unsigned) [BigInteger]
 * (JVM/Android only).
 */
fun Sha256Hash.toBigInteger(): BigInteger = BigInteger(1, bytes)

/**
 * Hashes the full contents of [file] and returns the result wrapped in a
 * [Sha256Hash] (JVM/Android only).
 */
fun Sha256Hash.Companion.of(file: File): Sha256Hash = of(file.readBytes())
