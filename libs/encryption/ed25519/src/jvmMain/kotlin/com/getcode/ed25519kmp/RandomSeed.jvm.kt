package com.getcode.ed25519kmp

import java.security.SecureRandom

private val random = SecureRandom()

internal actual fun randomSeed(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)
