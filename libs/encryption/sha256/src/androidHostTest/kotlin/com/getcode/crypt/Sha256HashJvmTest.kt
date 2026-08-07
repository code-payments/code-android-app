package com.getcode.crypt

import kotlin.test.Test
import kotlin.test.assertTrue

class Sha256HashJvmTest {

    /** [Sha256Hash.toBigInteger] must return a positive value for a non-zero hash. */
    @Test
    fun toBigIntegerPositive() {
        val hash = Sha256Hash.wrap(ByteArray(32) { 0xff.toByte() })
        assertTrue(hash.toBigInteger().signum() > 0)
    }
}
