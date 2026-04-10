package com.flipcash.app.core.verification

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Result returned by the Verification flow to its caller.
 */
@Serializable
sealed interface VerificationResult : Parcelable {
    @Parcelize
    @Serializable
    data object Success : VerificationResult

    @Parcelize
    @Serializable
    data object Canceled : VerificationResult
}
