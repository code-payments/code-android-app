package com.flipcash.app.internal.ui.navigation

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.router.Router
import com.flipcash.services.user.AuthState
import dev.theolm.rinku.DeepLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildNavGraphForLaunchTest {

    // -- Helpers --

    /** Router that returns a fixed action for any deeplink. */
    private class FakeRouter(private val action: DeeplinkAction) : Router {
        override fun dispatch(deepLink: DeepLink): DeeplinkAction = action
        override fun classify(deepLink: DeepLink): DeeplinkType? = null
    }

    private val dummyLink = DeepLink("https://send.flipcash.com/c/e=testEntropy")

    private fun build(
        state: AuthState,
        action: DeeplinkAction = DeeplinkAction.None,
        deepLink: DeepLink? = null,
    ): LaunchNavGraph? = buildNavGraphForLaunch(
        state = state,
        router = FakeRouter(action),
        deepLink = { deepLink },
    )

    // -- Ready --

    @Test
    fun `logged in without deeplink navigates to Scanner`() {
        val result = build(AuthState.Ready)!!
        assertEquals(listOf(AppRoute.Main.Scanner), result.baseRoutes)
        assertTrue(result.deeplinkRoutes.isEmpty())
    }

    @Test
    fun `logged in with Navigate deeplink includes deeplink routes`() {
        val routes = listOf(AppRoute.Main.Scanner)
        val result = build(
            state = AuthState.Ready,
            action = DeeplinkAction.Navigate(routes),
            deepLink = dummyLink,
        )!!
        assertEquals(listOf(AppRoute.Main.Scanner), result.baseRoutes)
        assertEquals(routes, result.deeplinkRoutes)
    }

    @Test
    fun `logged in with OpenCashLink fires eagerly via pendingAction`() {
        val action = DeeplinkAction.OpenCashLink("testEntropy")
        val result = build(
            state = AuthState.Ready,
            action = action,
            deepLink = dummyLink,
        )!!
        assertEquals(listOf(AppRoute.Main.Scanner), result.baseRoutes)
        assertTrue(result.deeplinkRoutes.isEmpty())
        assertEquals(action, result.pendingAction)
    }

    @Test
    fun `logged in with Login action fires eagerly via pendingAction`() {
        val action = DeeplinkAction.Login("seed")
        val result = build(
            state = AuthState.Ready,
            action = action,
            deepLink = dummyLink,
        )!!
        assertEquals(listOf(AppRoute.Main.Scanner), result.baseRoutes)
        assertTrue(result.deeplinkRoutes.isEmpty())
        assertEquals(action, result.pendingAction)
    }

    @Test
    fun `logged in with None action navigates to Scanner without deeplink routes`() {
        val result = build(
            state = AuthState.Ready,
            action = DeeplinkAction.None,
            deepLink = dummyLink,
        )!!
        assertEquals(listOf(AppRoute.Main.Scanner), result.baseRoutes)
        assertTrue(result.deeplinkRoutes.isEmpty())
    }

    // -- LoggedOut / Unknown --

    @Test
    fun `logged out without deeplink navigates to OnboardingFlow`() {
        val result = build(AuthState.LoggedOut)!!
        assertIs<AppRoute.OnboardingFlow>(result.baseRoutes.single())
    }

    @Test
    fun `logged out with Navigate deeplink uses action routes`() {
        val routes = listOf(AppRoute.OnboardingFlow(seed = "abc"))
        val result = build(
            state = AuthState.LoggedOut,
            action = DeeplinkAction.Navigate(routes),
            deepLink = dummyLink,
        )!!
        assertEquals(routes, result.baseRoutes)
    }

    @Test
    fun `logged out with OpenCashLink falls back to OnboardingFlow`() {
        val result = build(
            state = AuthState.LoggedOut,
            action = DeeplinkAction.OpenCashLink("entropy"),
            deepLink = dummyLink,
        )!!
        assertIs<AppRoute.OnboardingFlow>(result.baseRoutes.single())
    }

    @Test
    fun `unknown auth state without deeplink navigates to OnboardingFlow`() {
        val result = build(AuthState.Unknown)!!
        assertIs<AppRoute.OnboardingFlow>(result.baseRoutes.single())
    }

    // -- Onboarding --

    @Test
    fun `onboarding at AccessKey resume point routes to AccessKey`() {
        val result = build(AuthState.Onboarding(AuthState.ResumePoint.AccessKey))!!
        val route = assertIs<AppRoute.OnboardingFlow>(result.baseRoutes.single())
        assertEquals(AppRoute.OnboardingFlow.ResumePoint.AccessKey, route.resumeAt)
    }

    @Test
    fun `onboarding at PostAccessKey resume point routes to PostAccessKey`() {
        val result = build(AuthState.Onboarding(AuthState.ResumePoint.PostAccessKey))!!
        val route = assertIs<AppRoute.OnboardingFlow>(result.baseRoutes.single())
        assertEquals(AppRoute.OnboardingFlow.ResumePoint.PostAccessKey, route.resumeAt)
    }

    @Test
    fun `onboarding at AccessKeyThenPurchase resume point routes to AccessKeyThenPurchase`() {
        val result = build(AuthState.Onboarding(AuthState.ResumePoint.AccessKeyThenPurchase))!!
        val route = assertIs<AppRoute.OnboardingFlow>(result.baseRoutes.single())
        assertEquals(AppRoute.OnboardingFlow.ResumePoint.AccessKeyThenPurchase, route.resumeAt)
    }

    // -- Authenticating --

    @Test
    fun `authenticating returns null`() {
        assertNull(build(AuthState.Authenticating))
    }
}
