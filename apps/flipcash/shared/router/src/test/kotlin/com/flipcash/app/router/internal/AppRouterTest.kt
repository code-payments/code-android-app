package com.flipcash.app.router.internal

import android.util.Base64
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.services.user.AuthState
import com.getcode.solana.keys.Mint
import dev.theolm.rinku.DeepLink
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppRouterTest {

    private var authState: AuthState = AuthState.Ready

    private val router = AppRouter(authStateProvider = { authState })

    private fun loggedIn() { authState = AuthState.Ready }
    private fun loggedOut() { authState = AuthState.LoggedOut }

    // region classify — Login

    @Test
    fun `classify recognizes login deeplink with entropy fragment`() {
        val type = router.classify(DeepLink("https://app.flipcash.com/login/e=abc123"))
        assertIs<DeeplinkType.Login>(type)
        assertEquals("abc123", type.entropy)
    }

    @Test
    fun `classify recognizes login deeplink with data query param`() {
        val type = router.classify(DeepLink("https://app.flipcash.com/login?data=xyz789"))
        assertIs<DeeplinkType.Login>(type)
        assertEquals("xyz789", type.entropy)
    }

    @Test
    fun `classify returns null for login without entropy`() {
        val type = router.classify(DeepLink("https://app.flipcash.com/login"))
        assertNull(type)
    }

    // endregion

    // region classify — CashLink

    @Test
    fun `classify recognizes cash link with c prefix`() {
        val type = router.classify(DeepLink("https://app.flipcash.com/c/e=someEntropy"))
        assertIs<DeeplinkType.CashLink>(type)
        assertEquals("someEntropy", type.entropy)
    }

    @Test
    fun `classify recognizes cash link with cash prefix`() {
        val type = router.classify(DeepLink("https://app.flipcash.com/cash/e=someEntropy"))
        assertIs<DeeplinkType.CashLink>(type)
        assertEquals("someEntropy", type.entropy)
    }

    @Test
    fun `classify returns null for cash link without entropy`() {
        val type = router.classify(DeepLink("https://app.flipcash.com/c/other"))
        assertNull(type)
    }

    // endregion

    // region classify — TokenInfo

    @Test
    fun `classify recognizes token info deeplink`() {
        val mintAddress = "So11111111111111111111111111111111111111112"
        val type = router.classify(DeepLink("https://app.flipcash.com/token/$mintAddress"))
        assertIs<DeeplinkType.TokenInfo>(type)
        assertEquals(Mint(mintAddress), type.mint)
    }

    // endregion

    // region classify — EmailVerification

    @Test
    fun `classify recognizes email verification deeplink`() {
        val type = router.classify(
            DeepLink("https://app.flipcash.com/verify?email=test%40example.com&code=123456")
        )
        assertIs<DeeplinkType.EmailVerification>(type)
        assertEquals("test@example.com", type.email)
        assertEquals("123456", type.code)
    }

    @Test
    fun `classify returns null for verify without email`() {
        val type = router.classify(DeepLink("https://app.flipcash.com/verify?code=123456"))
        assertNull(type)
    }

    @Test
    fun `classify parses email verification with client data origin`() {
        val origin = Base64.encodeToString("myaccount".toByteArray(), Base64.NO_WRAP)
        val clientData = """{"origin":"$origin"}"""
        val url = "https://app.flipcash.com/verify" +
                "?email=test%40example.com" +
                "&code=123456" +
                "&client_data=${URLEncoder.encode(clientData, "UTF-8")}"

        val type = router.classify(DeepLink(url))
        assertIs<DeeplinkType.EmailVerification>(type)
        assertEquals("myaccount", type.origin)
    }

    // endregion

    // region classify — Unknown

    @Test
    fun `classify returns null for unknown path`() {
        val type = router.classify(DeepLink("https://app.flipcash.com/unknown/path"))
        assertNull(type)
    }

    @Test
    fun `classify returns null for old external wallet paths`() {
        val type = router.classify(DeepLink("https://app.flipcash.com/external/phantom/connected?origin=menu"))
        assertNull(type)
    }

    // endregion

    // region dispatch — Not logged in

    @Test
    fun `dispatch redirects login deeplink to onboarding flow with seed when logged out`() {
        loggedOut()
        val action = router.dispatch(DeepLink("https://app.flipcash.com/login/e=seed123"))
        assertIs<DeeplinkAction.Navigate>(action)
        val route = action.routes.single()
        assertIs<AppRoute.OnboardingFlow>(route)
        assertEquals("seed123", route.seed)
        assertTrue(route.fromDeeplink)
    }

    @Test
    fun `dispatch redirects non-login deeplink to plain onboarding flow when logged out`() {
        loggedOut()
        val mintAddress = "So11111111111111111111111111111111111111112"
        val action = router.dispatch(DeepLink("https://app.flipcash.com/token/$mintAddress"))
        assertIs<DeeplinkAction.Navigate>(action)
        val route = action.routes.single()
        assertIs<AppRoute.OnboardingFlow>(route)
        assertNull(route.seed)
    }

    @Test
    fun `dispatch redirects cash link to onboarding flow when auth state is unknown`() {
        authState = AuthState.Unknown
        val action = router.dispatch(DeepLink("https://app.flipcash.com/c/e=entropy"))
        assertIs<DeeplinkAction.Navigate>(action)
        assertIs<AppRoute.OnboardingFlow>(action.routes.single())
    }

    // endregion

    // region dispatch — Logged in: Login

    @Test
    fun `dispatch returns Login action for login deeplink when logged in`() {
        loggedIn()
        val action = router.dispatch(DeepLink("https://app.flipcash.com/login/e=seed456"))
        assertIs<DeeplinkAction.Login>(action)
        assertEquals("seed456", action.entropy)
    }

    // endregion

    // region dispatch — Logged in: CashLink

    @Test
    fun `dispatch returns OpenCashLink for cash deeplink when logged in`() {
        loggedIn()
        val action = router.dispatch(DeepLink("https://app.flipcash.com/c/e=cashEntropy"))
        assertIs<DeeplinkAction.OpenCashLink>(action)
        assertEquals("cashEntropy", action.entropy)
    }

    @Test
    fun `dispatch returns OpenCashLink for cash prefix deeplink`() {
        loggedIn()
        val action = router.dispatch(DeepLink("https://app.flipcash.com/cash/e=moreEntropy"))
        assertIs<DeeplinkAction.OpenCashLink>(action)
        assertEquals("moreEntropy", action.entropy)
    }

    // endregion

    // region dispatch — Logged in: TokenInfo (sheet navigation)

    @Test
    fun `dispatch returns Navigate with wallet sheet and token info for token deeplink`() {
        loggedIn()
        val mint = "So11111111111111111111111111111111111111112"
        val action = router.dispatch(DeepLink("https://app.flipcash.com/token/$mint"))
        assertIs<DeeplinkAction.Navigate>(action)
        assertEquals(2, action.routes.size)
        assertIs<AppRoute.Sheets.Wallet>(action.routes[0])
        val tokenInfo = action.routes[1]
        assertIs<AppRoute.Token.Info>(tokenInfo)
        assertEquals(Mint(mint), tokenInfo.mint)
        assertTrue(tokenInfo.fromDeeplink)
    }

    // endregion

    // region dispatch — Logged in: EmailVerification (route building)

    @Test
    fun `dispatch builds my account routes for email verification`() {
        loggedIn()
        val origin = Base64.encodeToString("myaccount".toByteArray(), Base64.NO_WRAP)
        val clientData = """{"origin":"$origin"}"""
        val url = "https://app.flipcash.com/verify" +
                "?email=test%40example.com" +
                "&code=123456" +
                "&client_data=${URLEncoder.encode(clientData, "UTF-8")}"

        val action = router.dispatch(DeepLink(url))
        assertIs<DeeplinkAction.Navigate>(action)
        assertEquals(3, action.routes.size)
        assertIs<AppRoute.Sheets.Menu>(action.routes[0])
        assertIs<AppRoute.Menu.MyAccount>(action.routes[1])
        val verification = action.routes[2]
        assertIs<AppRoute.Verification>(verification)
        assertEquals("test@example.com", verification.email)
        assertEquals("123456", verification.emailVerificationCode)
        assertEquals(false, verification.includePhone)
    }

    @Test
    fun `dispatch returns None for email verification from menu onramp (unsupported source)`() {
        loggedIn()
        val origin = Base64.encodeToString("onramp|menu|null".toByteArray(), Base64.NO_WRAP)
        val clientData = """{"origin":"$origin"}"""
        val url = "https://app.flipcash.com/verify" +
                "?email=user%40mail.com" +
                "&code=654321" +
                "&client_data=${URLEncoder.encode(clientData, "UTF-8")}"

        val action = router.dispatch(DeepLink(url))
        assertIs<DeeplinkAction.None>(action)
    }

    @Test
    fun `dispatch returns None for email verification with unknown origin`() {
        loggedIn()
        val origin = Base64.encodeToString("unknown".toByteArray(), Base64.NO_WRAP)
        val clientData = """{"origin":"$origin"}"""
        val url = "https://app.flipcash.com/verify" +
                "?email=test%40example.com" +
                "&code=123456" +
                "&client_data=${URLEncoder.encode(clientData, "UTF-8")}"

        val action = router.dispatch(DeepLink(url))
        assertIs<DeeplinkAction.None>(action)
    }

    @Test
    fun `dispatch returns None for email verification without client data`() {
        loggedIn()
        val action = router.dispatch(
            DeepLink("https://app.flipcash.com/verify?email=test%40example.com&code=123456")
        )
        assertIs<DeeplinkAction.None>(action)
    }

    // endregion

    // region dispatch — None

    @Test
    fun `dispatch returns None for unknown deeplink`() {
        loggedIn()
        val action = router.dispatch(DeepLink("https://app.flipcash.com/unknown"))
        assertIs<DeeplinkAction.None>(action)
    }

    // endregion

    // region dispatch — Auth state transitions

    @Test
    fun `dispatch respects auth state changes between calls`() {
        loggedOut()
        val loginUrl = "https://app.flipcash.com/login/e=seed"

        // Logged out: should redirect to onboarding flow
        val action1 = router.dispatch(DeepLink(loginUrl))
        assertIs<DeeplinkAction.Navigate>(action1)
        assertIs<AppRoute.OnboardingFlow>(action1.routes.single())

        // Log in
        loggedIn()

        // Now should return Login action
        val action2 = router.dispatch(DeepLink(loginUrl))
        assertIs<DeeplinkAction.Login>(action2)
        assertEquals("seed", action2.entropy)
    }

    // endregion

    // region classify/dispatch consistency

    @Test
    fun `dispatch and classify agree on the deeplink type`() {
        loggedIn()
        val mint = "So11111111111111111111111111111111111111112"
        val deepLink = DeepLink("https://app.flipcash.com/token/$mint")

        val classified = router.classify(deepLink)
        assertIs<DeeplinkType.TokenInfo>(classified)

        val dispatched = router.dispatch(deepLink)
        assertIs<DeeplinkAction.Navigate>(dispatched)
        val tokenInfo = dispatched.routes[1]
        assertIs<AppRoute.Token.Info>(tokenInfo)
        assertEquals(classified.mint, tokenInfo.mint)
    }

    // endregion
}
