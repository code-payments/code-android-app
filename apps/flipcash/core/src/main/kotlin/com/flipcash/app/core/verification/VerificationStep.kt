package com.flipcash.app.core.verification

import android.os.Parcelable
import com.getcode.navigation.flow.FlowStep
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Steps inside the Verification flow. Owned by [com.flipcash.app.core.AppRoute.Verification]
 * and rendered inside a [com.getcode.navigation.flow.FlowHost].
 */
@Serializable
sealed interface VerificationStep : FlowStep, Parcelable {
    @Parcelize
    @Serializable
    data class Intro(val isForOnRamp: Boolean) : VerificationStep

    @Parcelize
    @Serializable
    data object PhoneEntry : VerificationStep

    @Parcelize
    @Serializable
    data object PhoneCode : VerificationStep

    @Parcelize
    @Serializable
    data object PhoneCountryCode : VerificationStep

    @Parcelize
    @Serializable
    data object EmailEntry : VerificationStep

    @Parcelize
    @Serializable
    data class EmailMagicLink(
        val email: String? = null,
        val code: String? = null,
    ) : VerificationStep
}
