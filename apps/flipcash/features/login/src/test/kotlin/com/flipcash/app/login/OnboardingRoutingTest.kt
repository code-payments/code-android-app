package com.flipcash.app.login

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.homeRoute
import com.flipcash.app.core.navigation.launchRoute
import com.flipcash.app.core.onboarding.OnboardingResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingRoutingTest {

    // -- Path 1 & 2: ProceedToVerification always routes to permissions --

    @Test
    fun `ProceedToVerification routes to permissions phase`() {
        val route = resolvePostAccountRoute(
            result = OnboardingResult.ProceedToVerification,
        )
        val flow = assertIs<AppRoute.OnboardingFlow>(route)
        assertEquals(AppRoute.OnboardingFlow.Phase.Permissions, flow.phase)
        assertEquals(true, flow.skipContacts)
        assertTrue(flow.newAccount)
    }

    // -- LoggedIn routes to permissions --

    @Test
    fun `LoggedIn routes to permissions phase`() {
        val route = resolvePostAccountRoute(
            result = OnboardingResult.LoggedIn,
        )
        val flow = assertIs<AppRoute.OnboardingFlow>(route)
        assertEquals(AppRoute.OnboardingFlow.Phase.Permissions, flow.phase)
        assertEquals(true, flow.skipContacts)
        assertFalse(flow.newAccount)
    }

    // -- skipContacts is forwarded --

    @Test
    fun `skipContacts is forwarded to permissions route`() {
        val route = resolvePostAccountRoute(
            result = OnboardingResult.ProceedToVerification,
            skipContacts = false,
        )
        val flow = assertIs<AppRoute.OnboardingFlow>(route)
        assertEquals(AppRoute.OnboardingFlow.Phase.Permissions, flow.phase)
        assertEquals(false, flow.skipContacts)
    }

    // -- Completed (no-op) --

    @Test
    fun `Completed returns null`() {
        assertNull(resolvePostAccountRoute(OnboardingResult.Completed))
    }

    // -- Where onboarding lands --

    @Test
    fun `a new account lands on the wallet`() {
        val route = AppRoute.OnboardingFlow(
            phase = AppRoute.OnboardingFlow.Phase.Permissions,
            newAccount = true,
        )
        assertEquals(homeRoute, onboardingLandingRoute(route))
    }

    @Test
    fun `an existing account lands where a cold launch does`() {
        val route = AppRoute.OnboardingFlow(phase = AppRoute.OnboardingFlow.Phase.Permissions)
        assertEquals(launchRoute, onboardingLandingRoute(route))
    }
}
