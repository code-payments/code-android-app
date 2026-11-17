package com.flipcash.app.core.route

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.verification.VerificationStep
import com.flipcash.app.core.tokens.SwapPurpose
import com.getcode.solana.keys.Mint
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The verification flow's first screen for each entry point. Onboarding starts at phone entry
 * because the account is already registered and there is nothing to introduce; the profile and
 * on-ramp entry points keep the stacks they had before onboarding used the flow.
 */
class VerificationRouteTest {

    @Test
    fun `onboarding starts at phone entry with no intro`() {
        val route = AppRoute.Verification(
            origin = AppRoute.OnboardingFlow(),
            includePhone = true,
            includeEmail = true,
            onboarding = true,
        )

        assertEquals(listOf(VerificationStep.PhoneEntry), route.initialStack)
    }

    @Test
    fun `profile phone entry starts at phone entry`() {
        val route = AppRoute.Verification(
            origin = AppRoute.Menu.UserProfile,
            includePhone = true,
            includeEmail = false,
        )

        assertEquals(listOf(VerificationStep.PhoneEntry), route.initialStack)
    }

    @Test
    fun `profile email entry starts at email entry`() {
        val route = AppRoute.Verification(
            origin = AppRoute.Menu.UserProfile,
            includePhone = false,
            includeEmail = true,
        )

        assertEquals(listOf(VerificationStep.EmailEntry), route.initialStack)
    }

    @Test
    fun `on-ramp asking for phone and email starts at the intro`() {
        val route = AppRoute.Verification(
            origin = AppRoute.Token.Swap(SwapPurpose.Buy(Mint.usdf)),
            includePhone = true,
            includeEmail = true,
        )

        assertEquals(listOf(VerificationStep.Intro(isForOnRamp = false)), route.initialStack)
    }

    @Test
    fun `routes default to the standard mode`() {
        val route = AppRoute.Verification(origin = AppRoute.Menu.UserProfile)

        assertEquals(false, route.onboarding)
    }
}
