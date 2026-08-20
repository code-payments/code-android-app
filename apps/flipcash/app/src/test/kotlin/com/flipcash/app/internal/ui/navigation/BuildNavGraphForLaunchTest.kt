package com.flipcash.app.internal.ui.navigation

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.services.models.chat.ChatId
import com.getcode.solana.keys.Mint
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
        isNewUi: Boolean = false,
    ): LaunchNavGraph? = buildNavGraphForLaunch(
        state = state,
        router = FakeRouter(action),
        isNewUi = isNewUi,
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
    fun `unknown auth state waits on Loading`() {
        // Unknown is a transient pre-resolution state, grouped with Authenticating: it returns
        // null so the Loading screen stays put until auth resolves. A genuine no-account
        // resolves to LoggedOut (which routes to OnboardingFlow), covered separately.
        assertNull(build(AuthState.Unknown))
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

    @Test
    fun `onboarding at DisplayName resume point routes to display name entry then permissions`() {
        val result = build(AuthState.Onboarding(AuthState.ResumePoint.DisplayName))!!
        val route = assertIs<AppRoute.UpdateUserProfile>(result.baseRoutes.single())
        assertTrue(route.includeName)
        assertEquals(false, route.includePhoto)
        val target = assertIs<AppRoute.OnboardingFlow>(route.target)
        assertEquals(AppRoute.OnboardingFlow.Phase.Permissions, target.phase)
    }

    // -- Authenticating --

    @Test
    fun `authenticating returns null`() {
        assertNull(build(AuthState.Authenticating))
    }

    // -- Ready (v2 / NewUi) --

    private val mint = Mint("So11111111111111111111111111111111111111112")

    private fun buildV2(
        action: DeeplinkAction = DeeplinkAction.None,
        deepLink: DeepLink? = dummyLink,
    ) = build(AuthState.Ready, action, deepLink, isNewUi = true)!!

    @Test
    fun `v2 logged in without deeplink opens on the Wallet tab`() {
        val result = build(AuthState.Ready, isNewUi = true)!!
        assertEquals(listOf(AppRoute.Sheets.Wallet), result.baseRoutes)
        assertTrue(result.deeplinkRoutes.isEmpty())
        assertEquals(listOf(AppRoute.Sheets.Wallet), result.resolvedBackStack())
    }

    @Test
    fun `v2 token deeplink pushes token info onto the Wallet tab without a sheet`() {
        val result = buildV2(
            DeeplinkAction.Navigate(
                listOf(AppRoute.Sheets.Wallet, AppRoute.Token.Info(mint, fromDeeplink = true))
            )
        )

        val stack = result.resolvedBackStack()
        assertEquals(2, stack.size)
        assertEquals(AppRoute.Sheets.Wallet, stack[0])
        assertIs<AppRoute.Token.Info>(stack[1])
        assertTrue(stack.none { it is AppRoute.Main.Sheet }, "v2 must not wrap a tab home in a sheet")
    }

    @Test
    fun `v2 tip chat deeplink switches to the Chats tab instead of a sheet over Wallet`() {
        val result = buildV2(
            DeeplinkAction.Navigate(
                listOf(
                    AppRoute.Sheets.Tips(),
                    AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(ChatId(listOf(1, 2, 3, 4)))),
                )
            )
        )

        val stack = result.resolvedBackStack()
        assertEquals(2, stack.size)
        assertIs<AppRoute.Sheets.Tips>(stack[0])
        assertIs<AppRoute.Messaging.Chat>(stack[1])
        // The launch home must be replaced by the target tab, not left underneath it.
        assertTrue(stack.none { it == AppRoute.Sheets.Wallet })
        assertTrue(stack.none { it is AppRoute.Main.Sheet })
    }

    @Test
    fun `v2 email verification deeplink lands on the You tab without a sheet`() {
        val result = buildV2(
            DeeplinkAction.Navigate(
                listOf(
                    AppRoute.Sheets.Menu,
                    AppRoute.Menu.MyAccount,
                    AppRoute.Verification(
                        origin = AppRoute.Menu.MyAccount,
                        includePhone = false,
                        email = "test@example.com",
                        emailVerificationCode = "123456",
                    ),
                )
            )
        )

        val stack = result.resolvedBackStack()
        assertEquals(3, stack.size)
        assertEquals(AppRoute.Sheets.Menu, stack[0])
        assertTrue(stack.none { it is AppRoute.Main.Sheet })
    }

    @Test
    fun `v1 token deeplink still opens the wallet sheet`() {
        val result = build(
            state = AuthState.Ready,
            action = DeeplinkAction.Navigate(
                listOf(AppRoute.Sheets.Wallet, AppRoute.Token.Info(mint, fromDeeplink = true))
            ),
            deepLink = dummyLink,
            isNewUi = false,
        )!!

        val stack = result.resolvedBackStack()
        assertEquals(2, stack.size)
        assertEquals(AppRoute.Main.Scanner, stack[0])
        assertIs<AppRoute.Main.Sheet>(stack[1])
    }

    @Test
    fun `v2 pending actions still launch on the Wallet tab`() {
        val action = DeeplinkAction.OpenCashLink("testEntropy")
        val result = buildV2(action)
        assertEquals(listOf(AppRoute.Sheets.Wallet), result.baseRoutes)
        assertEquals(action, result.pendingAction)
    }
}
