package com.flipcash.app.core.onboarding

import android.os.Parcelable
import com.getcode.navigation.NonDismissableRoute
import com.getcode.navigation.flow.FlowStep
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Steps inside the Onboarding flow. Owned by [com.flipcash.app.core.AppRoute.OnboardingFlow]
 * and rendered inside a [com.getcode.navigation.flow.FlowHost].
 */
@Serializable
sealed interface OnboardingStep : FlowStep, Parcelable {
    @Parcelize
    @Serializable
    data class Start(val seed: String? = null, val fromDeeplink: Boolean = false) : OnboardingStep

    @Parcelize
    @Serializable
    data object SeedInput : OnboardingStep

    @Parcelize
    @Serializable
    data object AccessKey : OnboardingStep

    @Parcelize
    @Serializable
    data object AccessKeySavedLocation : OnboardingStep

    @Parcelize
    @Serializable
    data object Purchase : OnboardingStep

    @Parcelize
    @Serializable
    data object ContactPermission : OnboardingStep

    @Parcelize
    @Serializable
    data object NotificationPermission : OnboardingStep

    @Parcelize
    @Serializable
    data class NotificationPermissionRationale(
        val permanentlyDenied: Boolean = false,
    ) : OnboardingStep, NonDismissableRoute
}

@Serializable
sealed interface OnboardingResult : Parcelable {
    @Parcelize
    @Serializable
    data object ProceedToVerification : OnboardingResult

    @Parcelize
    @Serializable
    data object LoggedIn : OnboardingResult

    @Parcelize
    @Serializable
    data object Completed : OnboardingResult
}
