package com.getcode.opencode.model.core.errors

sealed class SodiumError {
    data class ConversionToCurveFailed(val root: Throwable? = null): Throwable(cause = root)
    data class SharedKeyFailed(val root: Throwable? = null): Throwable(cause = root)
    data class EncryptionFailed(val root: Throwable? = null): Throwable(cause = root)
    data class DecryptionFailed(val root: Throwable? = null): Throwable(cause = root)
}