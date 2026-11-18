package com.flipcash.app.contact.verification

import com.flipcash.analytics.AnalyticsEvent
import com.flipcash.analytics.OnrampStep
import com.flipcash.analytics.events.OnrampEvents
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.verification.VerificationResult

/** What the verification flow does to the outer back stack when it exits. */
internal sealed interface VerificationExit {
    /** Replace the whole stack with [route]. */
    data class ReplaceAll(val route: AppRoute) : VerificationExit

    /** Replace only the verification route with [route], keeping what is under it. */
    data class Replace(val route: AppRoute) : VerificationExit

    /** Pop the verification route, back to where it was opened. */
    data object Pop : VerificationExit

    /** Leave the stack alone: the flow stays on its current step. */
    data object Stay : VerificationExit
}

/**
 * Onboarding opens this flow over the onboarding route after the account is registered. On
 * success it replaces only itself, so onboarding stays at the root of the stack; it never pops
 * back to the intro, where Create would register a second account. Every other entry point exits
 * as it did before onboarding used the flow.
 */
internal fun resolveVerificationExit(
    route: AppRoute.Verification,
    result: VerificationResult,
): VerificationExit {
    val target = route.target
    val succeeded = result is VerificationResult.Success
    return when {
        route.onboarding && succeeded && target != null -> VerificationExit.Replace(target)
        route.onboarding -> VerificationExit.Stay
        succeeded && target != null -> VerificationExit.ReplaceAll(target)
        else -> VerificationExit.Pop
    }
}

/** The on-ramp step event a verification screen sends, or null in onboarding, which sends none. */
internal fun onrampStepEvent(step: OnrampStep, onboarding: Boolean): AnalyticsEvent? =
    if (onboarding) null else OnrampEvents.step(step)
