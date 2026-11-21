package com.flipcash.app.contact.verification

import com.flipcash.analytics.OnrampStep
import com.flipcash.analytics.events.OnrampEvents
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.DisplayNameSource
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.app.core.verification.VerificationResult
import com.getcode.solana.keys.Mint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Onboarding opens the verification flow after the account is registered, so the flow must never
 * leave onboarding on its own: it only moves forward to its target. The profile and on-ramp entry
 * points keep exiting the way they did before onboarding used the flow.
 */
class VerificationModeTest {

    private val nameStep = AppRoute.UpdateUserProfile(
        origin = AppRoute.OnboardingFlow(),
        steps = listOf(UpdateProfileStep.Name(DisplayNameSource.Onboarding)),
        target = AppRoute.OnboardingFlow(phase = AppRoute.OnboardingFlow.Phase.Permissions),
        allowBack = false,
    )

    private val onboarding = AppRoute.Verification(
        origin = AppRoute.OnboardingFlow(),
        onboarding = true,
        target = nameStep,
    )

    private val profilePhone = AppRoute.Verification(
        origin = AppRoute.Menu.UserProfile,
        includePhone = true,
        includeEmail = false,
    )

    private val onRamp = AppRoute.Verification(
        origin = AppRoute.Token.Swap(SwapPurpose.Buy(Mint.usdf)),
        includePhone = true,
        includeEmail = true,
    )

    @Test
    fun `onboarding success replaces only the verification route with its target`() {
        assertEquals(
            VerificationExit.Replace(nameStep),
            resolveVerificationExit(onboarding, VerificationResult.Success),
        )
    }

    @Test
    fun `onboarding cancel stays in the flow`() {
        assertEquals(
            VerificationExit.Stay,
            resolveVerificationExit(onboarding, VerificationResult.Canceled),
        )
    }

    @Test
    fun `profile success and cancel pop back to the profile`() {
        assertEquals(VerificationExit.Pop, resolveVerificationExit(profilePhone, VerificationResult.Success))
        assertEquals(VerificationExit.Pop, resolveVerificationExit(profilePhone, VerificationResult.Canceled))
    }

    @Test
    fun `on-ramp success and cancel pop back to the swap`() {
        assertEquals(VerificationExit.Pop, resolveVerificationExit(onRamp, VerificationResult.Success))
        assertEquals(VerificationExit.Pop, resolveVerificationExit(onRamp, VerificationResult.Canceled))
    }

    @Test
    fun `a standard route with a target still replaces the whole stack on success`() {
        val route = profilePhone.copy(target = AppRoute.Menu.UserProfile)

        assertEquals(
            VerificationExit.ReplaceAll(AppRoute.Menu.UserProfile),
            resolveVerificationExit(route, VerificationResult.Success),
        )
        assertEquals(VerificationExit.Pop, resolveVerificationExit(route, VerificationResult.Canceled))
    }

    @Test
    fun `onboarding sends no onramp step events`() {
        listOf(OnrampStep.ENTER_PHONE, OnrampStep.CONFIRM_PHONE, OnrampStep.ENTER_EMAIL).forEach { step ->
            assertNull(onrampStepEvent(step, onboarding = true), "$step")
        }
    }

    @Test
    fun `standard routes send the same onramp step events as before`() {
        listOf(OnrampStep.ENTER_PHONE, OnrampStep.CONFIRM_PHONE, OnrampStep.ENTER_EMAIL).forEach { step ->
            assertEquals(OnrampEvents.step(step), onrampStepEvent(step, onboarding = false))
        }
    }
}
