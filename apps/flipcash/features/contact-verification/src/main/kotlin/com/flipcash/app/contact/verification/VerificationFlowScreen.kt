package com.flipcash.app.contact.verification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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

    val initialStack = remember(route) {
        @Suppress("UNCHECKED_CAST")
        route.initialStack as List<VerificationStep>
    }

    LaunchedEffect(Unit) {
        PhoneVerificationFlow.start(route.origin)
        EmailVerificationFlow.start(EmailDeeplinkOrigin.fromRoute(route.origin))
    }

    FlowHost(
        initialStack = initialStack,
        resultStateRegistry = resultStateRegistry,
        onExit = { reason ->
            val result: VerificationResult = when (reason) {
                is FlowExitReason.Completed -> reason.result
                FlowExitReason.Canceled,
                FlowExitReason.BackedOutOfRoot -> VerificationResult.Canceled
            }
            outerNavigator.deliverFlowResult(
                route = route,
                value = NavResultOrCanceled.ReturnValue(result),
            )
            outerNavigator.pop()
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
        PhoneCodeContent(includeEmail = route.includeEmail)
    }
    annotatedEntry<VerificationStep.PhoneCountryCode> {
        PhoneCountryCodeContent()
    }
    annotatedEntry<VerificationStep.EmailEntry> {
        EmailVerificationContent()
    }
    annotatedEntry<VerificationStep.EmailMagicLink> { step ->
        EmailMagicLinkContent(email = step.email, code = step.code)
    }
}
