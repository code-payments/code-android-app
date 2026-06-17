package com.flipcash.app.contact.verification

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.flipcash.app.contact.verification.email.EmailMagicLinkContent
import com.flipcash.app.contact.verification.email.EmailVerificationContent
import com.flipcash.app.contact.verification.internal.VerificationFlowIntroContent
import com.flipcash.app.contact.verification.phone.PhoneCodeContent
import com.flipcash.app.contact.verification.phone.PhoneCountryCodeContent
import com.flipcash.app.contact.verification.phone.PhoneVerificationContent
import com.flipcash.app.core.AppRoute
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
            if (route.target != null && result is VerificationResult.Success) {
                outerNavigator.replaceAll(route.target!!)
            } else {
                outerNavigator.pop()
            }
        },
        entryProvider = verificationEntryProvider(route),
    )
}

private fun verificationEntryProvider(
    route: AppRoute.Verification,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<VerificationStep.Intro> { step ->
        VerificationFlowIntroContent(isForOnRamp = step.isForOnRamp)
    }
    annotatedEntry<VerificationStep.PhoneEntry> {
        PhoneVerificationContent()
    }
    annotatedEntry<VerificationStep.PhoneCode> {
        PhoneCodeContent(
            includeEmail = route.includeEmail,
            linkForPayment = route.linkForPayment,
        )
    }
    annotatedEntry<VerificationStep.PhoneCountryCode> {
        PhoneCountryCodeContent()
    }
    annotatedEntry<VerificationStep.EmailEntry> {
        EmailVerificationContent(
            origin = EmailDeeplinkOrigin.fromRoute(route.origin),
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
