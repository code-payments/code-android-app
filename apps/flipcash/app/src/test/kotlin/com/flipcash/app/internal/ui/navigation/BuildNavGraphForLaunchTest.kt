package com.flipcash.app.internal.ui.navigation

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.DisplayNameSource
import com.flipcash.app.core.userprofile.UpdateProfileStep
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

    private companion object {
        const val MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
    }

    private fun build(
        state: AuthState,
        action: DeeplinkAction = DeeplinkAction.None,
        deepLink: DeepLink? = null,
    ): LaunchNavGraph? = buildNavGraphForLaunch(
        state = state,
        router = FakeRouter(action),
        deepLink = { deepLink },
    )

    private fun buildReady(
        action: DeeplinkAction = DeeplinkAction.None,
        deepLink: DeepLink? = dummyLink,
    ) = build(AuthState.Ready, action, deepLink)!!

    // -- Ready --

    @Test
    fun `logged in without deeplink opens on the Wallet tab`() {
        val result = build(AuthState.Ready)!!
        assertEquals(listOf(AppRoute.Tabs.Wallet), result.baseRoutes)
        assertTrue(result.deeplinkRoutes.isEmpty())
        assertEquals(listOf(AppRoute.Tabs.Wallet), result.resolvedBackStack())
    }

    @Test
    fun `logged in with Navigate deeplink includes deeplink routes`() {
        val routes = listOf(AppRoute.Tabs.Scanner)
        val result = buildReady(DeeplinkAction.Navigate(routes))
        assertEquals(listOf(AppRoute.Tabs.Wallet), result.baseRoutes)
        assertEquals(routes, result.deeplinkRoutes)
    }

    @Test
    fun `logged in with OpenCashLink fires eagerly via pendingAction`() {
        val action = DeeplinkAction.OpenCashLink("testEntropy")
        val result = buildReady(action)
        assertEquals(listOf(AppRoute.Tabs.Wallet), result.baseRoutes)
        assertTrue(result.deeplinkRoutes.isEmpty())
        assertEquals(action, result.pendingAction)
    }

    @Test
    fun `logged in with Login action fires eagerly via pendingAction`() {
        val action = DeeplinkAction.Login("seed")
        val result = buildReady(action)
        assertEquals(listOf(AppRoute.Tabs.Wallet), result.baseRoutes)
        assertTrue(result.deeplinkRoutes.isEmpty())
        assertEquals(action, result.pendingAction)
    }

    @Test
    fun `a token deeplink opens as a pending action on the wallet home`() {
        // The expanded card is applied on top of the wallet, which is already the launch base --
        // no pushed screen, so nothing lands in deeplinkRoutes. See DeeplinkAction.OpenToken.
        val action = DeeplinkAction.OpenToken(
            mint = Mint(MINT),
            routes = listOf(AppRoute.Tabs.Wallet, AppRoute.Token.Info(Mint(MINT))),
        )
        val result = buildReady(action)
        assertEquals(listOf(AppRoute.Tabs.Wallet), result.baseRoutes)
        assertTrue(result.deeplinkRoutes.isEmpty())
        assertEquals(action, result.pendingAction)
    }

    @Test
    fun `logged in with None action opens the Wallet tab without deeplink routes`() {
        val result = buildReady(DeeplinkAction.None)
        assertEquals(listOf(AppRoute.Tabs.Wallet), result.baseRoutes)
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
        assertEquals(
            listOf(UpdateProfileStep.Name(DisplayNameSource.Onboarding)),
            route.steps,
        )
        val target = assertIs<AppRoute.OnboardingFlow>(route.target)
        assertEquals(AppRoute.OnboardingFlow.Phase.Permissions, target.phase)
    }

    // -- Authenticating --

    @Test
    fun `authenticating returns null`() {
        assertNull(build(AuthState.Authenticating))
    }

    // -- Deeplink back-stack resolution --

    private val mint = Mint("So11111111111111111111111111111111111111112")

    @Test
    fun `token deeplink pushes token info onto the Wallet tab without a sheet`() {
        val result = buildReady(
            DeeplinkAction.Navigate(
                listOf(AppRoute.Tabs.Wallet, AppRoute.Token.Info(mint, fromDeeplink = true))
            )
        )

        val stack = result.resolvedBackStack()
        assertEquals(2, stack.size)
        assertEquals(AppRoute.Tabs.Wallet, stack[0])
        assertIs<AppRoute.Token.Info>(stack[1])
        assertTrue(stack.none { it is AppRoute.Main.Sheet }, "a tab home must never be wrapped in a sheet")
    }

    @Test
    fun `tip chat deeplink switches to the Chats tab instead of a sheet over Wallet`() {
        val result = buildReady(
            DeeplinkAction.Navigate(
                listOf(
                    AppRoute.Tabs.Tips(),
                    AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(ChatId(listOf(1, 2, 3, 4)))),
                )
            )
        )

        val stack = result.resolvedBackStack()
        assertEquals(2, stack.size)
        assertIs<AppRoute.Tabs.Tips>(stack[0])
        assertIs<AppRoute.Messaging.Chat>(stack[1])
        // The launch home must be replaced by the target tab, not left underneath it.
        assertTrue(stack.none { it == AppRoute.Tabs.Wallet })
        assertTrue(stack.none { it is AppRoute.Main.Sheet })
    }

    @Test
    fun `email verification deeplink lands on the You tab without a sheet`() {
        val result = buildReady(
            DeeplinkAction.Navigate(
                listOf(
                    AppRoute.Tabs.Menu,
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
        assertEquals(AppRoute.Tabs.Menu, stack[0])
        assertTrue(stack.none { it is AppRoute.Main.Sheet })
    }

    @Test
    fun `pending actions still launch on the Wallet tab`() {
        val action = DeeplinkAction.OpenCashLink("testEntropy")
        val result = buildReady(action)
        assertEquals(listOf(AppRoute.Tabs.Wallet), result.baseRoutes)
        assertEquals(action, result.pendingAction)
    }
}
