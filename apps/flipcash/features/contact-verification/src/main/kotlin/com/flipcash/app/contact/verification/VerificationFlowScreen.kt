package com.flipcash.app.contact.verification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.flipcash.app.contact.verification.email.EmailMagicLinkContent
import com.flipcash.app.contact.verification.email.EmailVerificationContent
import com.flipcash.app.contact.verification.internal.VerificationIntroScreen
import com.flipcash.app.contact.verification.phone.PhoneCodeContent
import com.flipcash.app.contact.verification.phone.PhoneCountryCodeContent
import com.flipcash.app.contact.verification.phone.PhoneVerificationContent
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.ui.transitions.LocalHostEnterTransition
import com.flipcash.app.core.verification.VerificationResult
import com.flipcash.app.core.verification.VerificationStep
import com.flipcash.app.core.verification.email.EmailDeeplinkOrigin
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.FlowExitReason
import com.getcode.navigation.flow.rememberInitialStack
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.deliverFlowResult
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.NavResultStateRegistry

@Composable
fun VerificationFlowScreen(
    route: AppRoute.Verification,
    resultStateRegistry: NavResultStateRegistry,
) {
    // Capture the outer navigator before FlowHost overrides LocalCodeNavigator.
    val outerNavigator = LocalCodeNavigator.current

    val initialStack = route.rememberInitialStack<VerificationStep>()

    // The steps animate inside the flow's own nav display, so they can't see the slide that pushed
    // the whole flow. Hand them that transition so a field only takes focus once it has landed.
    val hostEnterTransition = LocalNavAnimatedContentScope.current.transition

    CompositionLocalProvider(LocalHostEnterTransition provides hostEnterTransition) {
        FlowHost(
            initialStack = initialStack,
            resultStateRegistry = resultStateRegistry,
            onExit = { reason, _ ->
                val result: VerificationResult = when (reason) {
                    is FlowExitReason.Completed -> reason.result
                    FlowExitReason.Canceled,
                    FlowExitReason.BackedOutOfRoot -> VerificationResult.Canceled
                }
                outerNavigator.deliverFlowResult(
                    route = route,
                    value = NavResultOrCanceled.ReturnValue(result),
                )
                when (val exit = resolveVerificationExit(route, result)) {
                    is VerificationExit.ReplaceAll -> outerNavigator.replaceAll(exit.route)
                    is VerificationExit.Replace -> outerNavigator.replace(exit.route)
                    VerificationExit.Pop -> outerNavigator.pop()
                    VerificationExit.Stay -> Unit
                }
            },
            entryProvider = verificationEntryProvider(route),
        )
    }
}

private fun verificationEntryProvider(
    route: AppRoute.Verification,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<VerificationStep.Intro> { step ->
        VerificationIntroScreen(step.isForOnRamp)
    }
    annotatedEntry<VerificationStep.PhoneEntry> {
        PhoneVerificationContent(onboarding = route.onboarding)
    }
    annotatedEntry<VerificationStep.PhoneCode> {
        PhoneCodeContent(
            includeEmail = route.includeEmail || route.onboarding,
            linkForPayment = route.linkForPayment,
            onboarding = route.onboarding,
        )
    }
    annotatedEntry<VerificationStep.PhoneCountryCode> {
        PhoneCountryCodeContent()
    }
    annotatedEntry<VerificationStep.EmailEntry> {
        EmailVerificationContent(
            origin = EmailDeeplinkOrigin.fromRoute(route.origin),
            onboarding = route.onboarding,
        )
    }
    annotatedEntry<VerificationStep.EmailMagicLink> { step ->
        EmailMagicLinkContent(
            origin = EmailDeeplinkOrigin.fromRoute(route.origin),
            email = step.email,
            code = step.code,
        )
    }
}
