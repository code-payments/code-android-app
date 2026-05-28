package com.flipcash.app.login

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.onboarding.OnboardingResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class OnboardingRoutingTest {

    // -- Path 1: New account (AccessKey exits with ProceedToVerification) --

    @Test
    fun `path 1 - new account routes through verification when phone not linked`() {
        val route = resolvePostAccountRoute(
            result = OnboardingResult.ProceedToVerification,
            hasLinkedPhone = false,
        )
        val verification = assertIs<AppRoute.Verification>(route)
        val target = assertIs<AppRoute.OnboardingFlow>(verification.target)
        assertEquals(AppRoute.OnboardingFlow.Phase.Permissions, target.phase)
        assertEquals(false, target.skipContacts)
    }

    @Test
    fun `path 1 - new account skips verification when phone already linked`() {
        val route = resolvePostAccountRoute(
            result = OnboardingResult.ProceedToVerification,
            hasLinkedPhone = true,
        )
        val flow = assertIs<AppRoute.OnboardingFlow>(route)
        assertEquals(AppRoute.OnboardingFlow.Phase.Permissions, flow.phase)
        assertEquals(false, flow.skipContacts)
    }

    // -- Path 2: Seed restore (SeedInput exits with LoggedIn, Purchase exits with ProceedToVerification) --

    @Test
    fun `path 2 - seed restore without IAP skips verification`() {
        val route = resolvePostAccountRoute(
            result = OnboardingResult.LoggedIn,
            hasLinkedPhone = false,
        )
        val flow = assertIs<AppRoute.OnboardingFlow>(route)
        assertEquals(AppRoute.OnboardingFlow.Phase.Permissions, flow.phase)
        assertEquals(false, flow.skipContacts)
    }

    @Test
    fun `path 2 - seed restore with IAP routes through verification when phone not linked`() {
        val route = resolvePostAccountRoute(
            result = OnboardingResult.ProceedToVerification,
            hasLinkedPhone = false,
        )
        val verification = assertIs<AppRoute.Verification>(route)
        val target = assertIs<AppRoute.OnboardingFlow>(verification.target)
        assertEquals(false, target.skipContacts)
    }

    @Test
    fun `path 2 - seed restore with IAP skips verification when phone linked`() {
        val route = resolvePostAccountRoute(
            result = OnboardingResult.ProceedToVerification,
            hasLinkedPhone = true,
        )
        val flow = assertIs<AppRoute.OnboardingFlow>(route)
        assertEquals(false, flow.skipContacts)
    }

    // -- Path 3: App resume (PostAccessKey, skipContacts = true) --

    @Test
    fun `path 3 - app resume skips verification and contacts when phone linked`() {
        val route = resolvePostAccountRoute(
            result = OnboardingResult.ProceedToVerification,
            hasLinkedPhone = true,
            skipContacts = true,
        )
        val flow = assertIs<AppRoute.OnboardingFlow>(route)
        assertEquals(AppRoute.OnboardingFlow.Phase.Permissions, flow.phase)
        assertEquals(true, flow.skipContacts)
    }

    @Test
    fun `path 3 - app resume routes through verification and skips contacts when phone not linked`() {
        val route = resolvePostAccountRoute(
            result = OnboardingResult.ProceedToVerification,
            hasLinkedPhone = false,
            skipContacts = true,
        )
        val verification = assertIs<AppRoute.Verification>(route)
        val target = assertIs<AppRoute.OnboardingFlow>(verification.target)
        assertEquals(AppRoute.OnboardingFlow.Phase.Permissions, target.phase)
        assertEquals(true, target.skipContacts)
    }

    // -- Completed (no-op) --

    @Test
    fun `completed result returns null`() {
        assertNull(resolvePostAccountRoute(OnboardingResult.Completed, hasLinkedPhone = false))
    }
}
