package com.getcode.opencode.model.core.errors

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class SodiumErrorTest {

    @Test
    fun `ConversionToCurveFailed is a Throwable`() {
        val error = SodiumError.ConversionToCurveFailed()
        assertIs<Throwable>(error)
    }

    @Test
    fun `SharedKeyFailed is a Throwable`() {
        val error = SodiumError.SharedKeyFailed()
        assertIs<Throwable>(error)
    }

    @Test
    fun `EncryptionFailed is a Throwable`() {
        val error = SodiumError.EncryptionFailed()
        assertIs<Throwable>(error)
    }

    @Test
    fun `DecryptionFailed is a Throwable`() {
        val error = SodiumError.DecryptionFailed()
        assertIs<Throwable>(error)
    }

    @Test
    fun `cause is propagated for ConversionToCurveFailed`() {
        val root = RuntimeException("test")
        val error = SodiumError.ConversionToCurveFailed(root)
        assertSame(root, error.cause)
    }

    @Test
    fun `cause is propagated for SharedKeyFailed`() {
        val root = RuntimeException("test")
        val error = SodiumError.SharedKeyFailed(root)
        assertSame(root, error.cause)
    }

    @Test
    fun `cause is propagated for EncryptionFailed`() {
        val root = RuntimeException("test")
        val error = SodiumError.EncryptionFailed(root)
        assertSame(root, error.cause)
    }

    @Test
    fun `cause is propagated for DecryptionFailed`() {
        val root = RuntimeException("test")
        val error = SodiumError.DecryptionFailed(root)
        assertSame(root, error.cause)
    }

    @Test
    fun `null cause works for all error types`() {
        assertNull(SodiumError.ConversionToCurveFailed().cause)
        assertNull(SodiumError.SharedKeyFailed().cause)
        assertNull(SodiumError.EncryptionFailed().cause)
        assertNull(SodiumError.DecryptionFailed().cause)
    }
}
