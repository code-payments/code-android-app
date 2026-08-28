package com.flipcash.app.router.internal

import android.util.Base64
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.AuthState
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.bytes
import com.getcode.solana.keys.Mint
import dev.theolm.rinku.DeepLink
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLEncoder
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppRouterTest {

    private companion object {
        const val MINT = "So11111111111111111111111111111111111111112"
    }

    private var authState: AuthState = AuthState.Ready
    private var currentUserId: ID? = null
    private var currentUsername: String? = null

    private val router = AppRouter(
        authStateProvider = { authState },
        currentUserProvider = { AppRouter.CurrentUser(currentUserId, currentUsername) },
    )

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

    /**
     * `Uri.getQueryParameter` already percent-decodes. Decoding its result a second time
     * corrupts any address whose local part carries an encoded `+` -- a plus-tagged address
     * would arrive as `user tag@`, and the verification code would then be checked against an
     * email the user never entered.
     */
    @Test
    fun `classify does not decode the email query parameter twice`() {
        val type = router.classify(
            DeepLink("https://app.flipcash.com/verify?email=user%2Btag%40example.com&code=123456")
        )
        assertIs<DeeplinkType.EmailVerification>(type)
        assertEquals("user+tag@example.com", type.email)
    }

    /**
     * A second decode is not just lossy, it throws: once `getQueryParameter` has turned `%25`
     * into a bare `%`, `URLDecoder` sees an incomplete escape and fails, which drops the whole
     * link. A literal `%` is legal in an address local part.
     */
    @Test
    fun `classify keeps a literal percent in the email instead of dropping the link`() {
        val type = router.classify(
            DeepLink("https://app.flipcash.com/verify?email=100%25off%40example.com&code=123456")
        )
        assertIs<DeeplinkType.EmailVerification>(type)
        assertEquals("100%off@example.com", type.email)
    }

    /**
     * Characterises the decode `handleEmailVerification` relies on. `Uri.getQueryParameter` is a
     * form decoder -- `%2B` comes back as `+`, a literal `+` comes back as a space -- which is the
     * exact inverse of the percent-plus-`+`-for-space encoder the client uses to build these
     * links (PhantomDeeplinkProtocol.urlEncode, and URLEncoder elsewhere). One decode round-trips
     * the producer; a second one does not.
     */
    @Test
    fun `query parameters are form-decoded exactly once`() {
        val type = router.classify(
            DeepLink("https://app.flipcash.com/verify?email=a%40b.com&code=enc%2Blit+sp")
        )
        assertIs<DeeplinkType.EmailVerification>(type)
        assertEquals("enc+lit sp", type.code)
    }

    /**
     * Same double-decode applied to `client_data`. The origin is standard base64, whose alphabet
     * includes `+`, so a second (form-semantics) decode turns that `+` into a space and the
     * origin silently fails to decode -- taking the routing destination with it.
     */
    @Test
    fun `classify does not decode client data twice`() {
        val origin = Base64.encodeToString("aa>".toByteArray(), Base64.NO_WRAP)
        assertTrue(origin.contains('+'), "fixture must exercise a '+' in the base64 payload")

        val clientData = """{"origin":"$origin"}"""
        val url = "https://app.flipcash.com/verify" +
                "?email=test%40example.com" +
                "&code=123456" +
                "&client_data=${URLEncoder.encode(clientData, "UTF-8")}"

        val type = router.classify(DeepLink(url))
        assertIs<DeeplinkType.EmailVerification>(type)
        assertEquals("aa>", type.origin)
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

    // region dispatch — Logged in: EmailVerification

    private fun verifyUrl(originPlain: String): String {
        val origin = Base64.encodeToString(originPlain.toByteArray(), Base64.NO_WRAP)
        val clientData = """{"origin":"$origin"}"""
        return "https://app.flipcash.com/verify" +
                "?email=test%40example.com" +
                "&code=123456" +
                "&client_data=${URLEncoder.encode(clientData, "UTF-8")}"
    }

    @Test
    fun `dispatch returns Navigate to menu tab for a myaccount verify deeplink`() {
        loggedIn()
        val action = router.dispatch(DeepLink(verifyUrl("myaccount")))
        assertIs<DeeplinkAction.Navigate>(action)
        assertEquals(AppRoute.Sheets.Menu, action.routes[0])
        assertIs<AppRoute.Menu.MyAccount>(action.routes[1])
        assertIs<AppRoute.Verification>(action.routes[2])
    }

    @Test
    fun `dispatch returns Navigate to the swap flow for an onramp verify deeplink`() {
        loggedIn()
        val action = router.dispatch(DeepLink(verifyUrl("onramp|amountentry|$MINT")))
        assertIs<DeeplinkAction.Navigate>(action)
        assertIs<AppRoute.Token.Info>(action.routes[0])
        assertIs<AppRoute.Token.Swap>(action.routes[1])
        assertIs<AppRoute.Verification>(action.routes[2])
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

    // region dispatch — Logged in: TokenInfo

    @Test
    fun `dispatch returns OpenToken carrying the mint for a token deeplink`() {
        loggedIn()
        val mint = "So11111111111111111111111111111111111111112"
        val action = router.dispatch(DeepLink("https://app.flipcash.com/token/$mint"))
        assertIs<DeeplinkAction.OpenToken>(action)
        assertEquals(Mint(mint), action.mint)
    }

    @Test
    fun `OpenToken carries the wallet sheet route form for v1`() {
        loggedIn()
        val mint = "So11111111111111111111111111111111111111112"
        val action = router.dispatch(DeepLink("https://app.flipcash.com/token/$mint"))
        assertIs<DeeplinkAction.OpenToken>(action)
        assertEquals(2, action.routes.size)
        assertIs<AppRoute.Sheets.Wallet>(action.routes[0])
        val tokenInfo = action.routes[1]
        assertIs<AppRoute.Token.Info>(tokenInfo)
        assertEquals(Mint(mint), tokenInfo.mint)
        assertTrue(tokenInfo.fromDeeplink)
    }

    // endregion

    // region classify + dispatch — TipChat

    private val sampleChatId = ChatId(ByteArray(32) { it.toByte() })

    @Test
    fun `classify recognizes tip chat deeplink and round-trips the chat id`() {
        val type = router.classify(DeepLink(Linkify.tipChatById(sampleChatId)))
        assertIs<DeeplinkType.TipChat>(type)
        val identifier = type.identifier
        assertIs<ChatIdentifier.ByChatId>(identifier)
        assertEquals(sampleChatId, identifier.chatId)
    }

    @Test
    fun `classify returns null for tip chat without chat id segment`() {
        val type = router.classify(DeepLink("https://app.flipcash.com/tip/chat"))
        assertNull(type)
    }

    @Test
    fun `classify still recognizes tip card deeplink (not swallowed by tip chat)`() {
        val userId = "11111111-1111-1111-1111-111111111111"
        val type = router.classify(DeepLink("https://app.flipcash.com/tip/$userId"))
        assertIs<DeeplinkType.Tipcard>(type)
    }

    @Test
    fun `dispatch returns Navigate with tips sheet and chat for tip chat deeplink`() {
        loggedIn()
        val action = router.dispatch(DeepLink(Linkify.tipChatById(sampleChatId)))
        assertIs<DeeplinkAction.Navigate>(action)
        assertEquals(2, action.routes.size)
        assertIs<AppRoute.Sheets.Tips>(action.routes[0])
        val chat = action.routes[1]
        assertIs<AppRoute.Messaging.Chat>(chat)
        val identifier = chat.identifier
        assertIs<ChatIdentifier.ByChatId>(identifier)
        assertEquals(sampleChatId, identifier.chatId)
    }

    @Test
    fun `dispatch presents the tip card for another user's tip card deeplink`() {
        loggedIn()
        currentUserId = UUID.fromString("22222222-2222-2222-2222-222222222222").bytes

        val userId = "11111111-1111-1111-1111-111111111111"
        val action = router.dispatch(DeepLink("https://app.flipcash.com/tip/$userId"))

        assertIs<DeeplinkAction.PresentTipCard>(action)
        assertEquals(TipCardOwner.ById(UUID.fromString(userId).bytes), action.owner)
    }

    @Test
    fun `dispatch routes your own tip card deeplink to the You tab`() {
        loggedIn()
        val userId = "11111111-1111-1111-1111-111111111111"
        currentUserId = UUID.fromString(userId).bytes

        val action = router.dispatch(DeepLink("https://app.flipcash.com/tip/$userId"))

        // Tipping yourself is a payment no-op, so the link lands on the You tab instead.
        assertIs<DeeplinkAction.Navigate>(action)
        assertEquals(listOf(AppRoute.Sheets.Menu), action.routes)
    }

    @Test
    fun `dispatch presents the tip card when the current user id is unknown`() {
        loggedIn()
        currentUserId = null

        val userId = "11111111-1111-1111-1111-111111111111"
        val action = router.dispatch(DeepLink("https://app.flipcash.com/tip/$userId"))

        assertIs<DeeplinkAction.PresentTipCard>(action)
    }

    // endregion

    // region classify + dispatch — tip card links on the bare host

    @Test
    fun `classify recognizes a vanity profile link`() {
        val type = router.classify(DeepLink(Linkify.tipcard(TipCardOwner.ByUsername("sally_streamer"))))
        assertIs<DeeplinkType.TipcardByUsername>(type)
        assertEquals("sally_streamer", type.username)
    }

    @Test
    fun `classify lowercases a vanity profile link`() {
        val type = router.classify(DeepLink("https://flipcash.com/Sally_Streamer"))
        assertIs<DeeplinkType.TipcardByUsername>(type)
        assertEquals("sally_streamer", type.username)
    }

    @Test
    fun `classify recognizes a vanity profile link on the www host`() {
        val type = router.classify(DeepLink("https://www.flipcash.com/sally_streamer"))
        assertIs<DeeplinkType.TipcardByUsername>(type)
    }

    @Test
    fun `classify ignores the website's own pages on the vanity host`() {
        assertNull(router.classify(DeepLink(Linkify.download("abc123"))))
        assertNull(router.classify(DeepLink("https://flipcash.com/privacy")))
        assertNull(router.classify(DeepLink("https://flipcash.com/terms")))
    }

    // The reserved list is Android's half of a pair: iOS says the same thing with the AASA's
    // `exclude` entries. Anything the website serves and this list misses is a page that opens the
    // app and dead-ends on "username not found", so the two are checked against each other.
    @Test
    fun `classify ignores every handle-shaped path the AASA excludes`() {
        val excludedByTheAasa = listOf(
            "app", "api", "assets", "fonts", "icons", "js", "v1",
            "pool", "wallet", "currencycreator",
            "blog", "download", "privacy", "support", "terms",
            "c", "cash", "chat", "tip", "token", "verify",
        )
        excludedByTheAasa.forEach { path ->
            assertNull(
                router.classify(DeepLink("https://flipcash.com/$path")),
                "flipcash.com/$path belongs to the website, not to a handle",
            )
        }
    }

    // Reserved by path, not by case: the App Link filter admits mixed case (a link is typed or
    // auto-capitalised), and isVanityProfile lowercases before consulting the list.
    @Test
    fun `classify ignores a reserved path in mixed case`() {
        assertNull(router.classify(DeepLink("https://flipcash.com/Download")))
        assertNull(router.classify(DeepLink("https://flipcash.com/CurrencyCreator")))
    }

    @Test
    fun `classify recognizes a tip card link addressed by account id`() {
        val userId = "11111111-2222-3333-4444-555555555555"
        val type = router.classify(DeepLink("https://flipcash.com/$userId"))
        assertIs<DeeplinkType.Tipcard>(type)
        assertEquals(UUID.fromString(userId).bytes, type.userId)
    }

    @Test
    fun `classify recognizes an id link written by Linkify`() {
        val userId = UUID.fromString("11111111-2222-3333-4444-555555555555").bytes
        val type = router.classify(DeepLink(Linkify.tipcard(TipCardOwner.ById(userId))))
        assertIs<DeeplinkType.Tipcard>(type)
        assertEquals(userId, type.userId)
    }

    // A link is typed, printed, or auto-capitalised in any case; the id it resolves to is the same.
    @Test
    fun `classify recognizes an id link in mixed case`() {
        val type = router.classify(DeepLink("https://flipcash.com/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE"))
        assertIs<DeeplinkType.Tipcard>(type)
        assertEquals(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee").bytes, type.userId)
    }

    @Test
    fun `classify recognizes an id link on the www host`() {
        val type = router.classify(DeepLink("https://www.flipcash.com/11111111-2222-3333-4444-555555555555"))
        assertIs<DeeplinkType.Tipcard>(type)
    }

    @Test
    fun `dispatch presents the tip card for another user's id link`() {
        loggedIn()
        currentUserId = UUID.fromString("22222222-2222-2222-2222-222222222222").bytes

        val userId = "11111111-1111-1111-1111-111111111111"
        val action = router.dispatch(DeepLink("https://flipcash.com/$userId"))

        assertIs<DeeplinkAction.PresentTipCard>(action)
        assertEquals(TipCardOwner.ById(UUID.fromString(userId).bytes), action.owner)
    }

    @Test
    fun `dispatch routes your own id link to the You tab`() {
        loggedIn()
        val userId = "11111111-1111-1111-1111-111111111111"
        currentUserId = UUID.fromString(userId).bytes

        val action = router.dispatch(DeepLink("https://flipcash.com/$userId"))

        assertIs<DeeplinkAction.Navigate>(action)
        assertEquals(AppRoute.Sheets.Menu, action.routes.single())
    }

    // UUID.fromString would take these; the regex doesn't. A shape the app doesn't claim goes back
    // to the browser rather than being captured from the website.
    @Test
    fun `classify ignores a path that only loosely resembles a UUID`() {
        assertNull(router.classify(DeepLink("https://flipcash.com/1-1-1-1-1")))
        assertNull(router.classify(DeepLink("https://flipcash.com/11111111-2222-3333-4444-5555555555")))
        assertNull(router.classify(DeepLink("https://flipcash.com/gggggggg-2222-3333-4444-555555555555")))
    }

    @Test
    fun `classify ignores a vanity path that isn't shaped like a handle`() {
        // Too short, too long, and outside the server's charset.
        assertNull(router.classify(DeepLink("https://flipcash.com/a")))
        assertNull(router.classify(DeepLink("https://flipcash.com/sixteencharacter")))
        assertNull(router.classify(DeepLink("https://flipcash.com/sally.streamer")))
    }

    @Test
    fun `classify ignores a multi-segment path on the vanity host`() {
        assertNull(router.classify(DeepLink("https://flipcash.com/sally/streamer")))
    }

    @Test
    fun `classify does not treat the app host as a vanity link`() {
        assertNull(router.classify(DeepLink("https://app.flipcash.com/sally_streamer")))
    }

    @Test
    fun `dispatch presents the tip card for a vanity profile link`() {
        loggedIn()
        val action = router.dispatch(DeepLink(Linkify.tipcard(TipCardOwner.ByUsername("sally_streamer"))))
        assertIs<DeeplinkAction.PresentTipCard>(action)
        assertEquals(TipCardOwner.ByUsername("sally_streamer"), action.owner)
    }

    // Your own handle diverts to the You tab here rather than after resolution: the session
    // announces a self-tip through a replay-less event only the scanner collects, so a link
    // opened onto any other tab would drop it silently.
    @Test
    fun `dispatch routes your own vanity profile link to the You tab`() {
        loggedIn()
        currentUsername = "sally_streamer"
        val action = router.dispatch(DeepLink(Linkify.tipcard(TipCardOwner.ByUsername("sally_streamer"))))
        assertIs<DeeplinkAction.Navigate>(action)
        assertEquals(AppRoute.Sheets.Menu, action.routes.single())
    }

    // A link can be typed or pasted in any case; handles are lowercase on the wire.
    @Test
    fun `dispatch matches your own handle regardless of case`() {
        loggedIn()
        currentUsername = "sally_streamer"
        val action = router.dispatch(DeepLink("https://flipcash.com/Sally_Streamer"))
        assertIs<DeeplinkAction.Navigate>(action)
        assertEquals(AppRoute.Sheets.Menu, action.routes.single())
    }

    @Test
    fun `dispatch routes a vanity profile link to onboarding when logged out`() {
        loggedOut()
        val action = router.dispatch(DeepLink(Linkify.tipcard(TipCardOwner.ByUsername("sally_streamer"))))
        assertIs<DeeplinkAction.Navigate>(action)
        assertIs<AppRoute.OnboardingFlow>(action.routes.single())
    }

    @Test
    fun `dispatch hands an unrouted vanity-host link back to the web`() {
        loggedIn()
        val action = router.dispatch(DeepLink("https://flipcash.com/privacy"))
        assertIs<DeeplinkAction.OpenExternally>(action)
        assertEquals("https://flipcash.com/privacy", action.url)
    }

    @Test
    fun `dispatch hands the vanity host back to the web even when logged out`() {
        // The escape hatch runs before the auth check: onboarding is no better a landing place for
        // a website link than the home screen is.
        loggedOut()
        assertIs<DeeplinkAction.OpenExternally>(router.dispatch(DeepLink(Linkify.download("abc123"))))
    }

    @Test
    fun `dispatch leaves an unrouted link on another host alone`() {
        loggedIn()
        assertEquals(DeeplinkAction.None, router.dispatch(DeepLink("https://app.flipcash.com/nonsense")))
        assertEquals(DeeplinkAction.None, router.dispatch(DeepLink("https://example.com/privacy")))
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

    // region classify — malformed client_data (must never throw)

    /**
     * `app.flipcash.com/verify` is an autoVerify App Link, so any web page can hand the app an
     * arbitrary `client_data`. Both dispatch call sites are fatal on throw (composition in
     * MainRoot, a LaunchedEffect in App), so every one of these must degrade to None.
     */
    private fun verifyUrlWithRawClientData(raw: String): String =
        "https://app.flipcash.com/verify" +
                "?email=test%40example.com" +
                "&code=123456" +
                "&client_data=${URLEncoder.encode(raw, "UTF-8")}"

    private fun verifyUrlWithOrigin(origin: String): String =
        verifyUrlWithRawClientData(
            """{"origin":"${Base64.encodeToString(origin.toByteArray(), Base64.NO_WRAP)}"}"""
        )

    @Test
    fun `dispatch returns None for onramp origin missing its source segment`() {
        loggedIn()
        // "onramp" alone used to index splits[1] unchecked.
        assertIs<DeeplinkAction.None>(router.dispatch(DeepLink(verifyUrlWithOrigin("onramp"))))
    }

    @Test
    fun `dispatch returns None for onramp origin with unparseable amount`() {
        loggedIn()
        val url = verifyUrlWithOrigin("onramp|amountentry|$MINT|garbage")
        val action = router.dispatch(DeepLink(url))
        // The mint still resolves, so this is a real Navigate — the point is that the junk
        // amount is dropped instead of throwing out of Json.decodeFromString.
        assertIs<DeeplinkAction.Navigate>(action)
    }

    @Test
    fun `dispatch returns None for onramp origin with blank mint`() {
        loggedIn()
        assertIs<DeeplinkAction.None>(
            router.dispatch(DeepLink(verifyUrlWithOrigin("onramp|amountentry|")))
        )
    }

    @Test
    fun `dispatch returns None for client data json without an origin key`() {
        loggedIn()
        assertIs<DeeplinkAction.None>(
            router.dispatch(DeepLink(verifyUrlWithRawClientData("""{"foo":1}""")))
        )
    }

    @Test
    fun `dispatch returns None for client data that is not json`() {
        loggedIn()
        assertIs<DeeplinkAction.None>(
            router.dispatch(DeepLink(verifyUrlWithRawClientData("notjson")))
        )
    }

    @Test
    fun `dispatch returns None for client data origin that is not base64`() {
        loggedIn()
        assertIs<DeeplinkAction.None>(
            router.dispatch(DeepLink(verifyUrlWithRawClientData("""{"origin":"!!!not base64!!!"}""")))
        )
    }

    // endregion

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
        assertIs<DeeplinkAction.OpenToken>(dispatched)
        assertEquals(classified.mint, dispatched.mint)
    }

    // endregion

    // region jump.flipcash.com — redirector unwrapping

    private fun jump(target: String) =
        DeepLink("https://jump.flipcash.com/#source=" + URLEncoder.encode(target, "UTF-8"))

    @Test
    fun `jump link unwraps to the wrapped token link`() {
        val type = router.classify(jump("https://app.flipcash.com/token/$MINT"))
        assertIs<DeeplinkType.TokenInfo>(type)
        assertEquals(Mint(MINT), type.mint)
    }

    @Test
    fun `jump link unwraps to the wrapped cash link`() {
        val type = router.classify(jump("https://send.flipcash.com/c/e=someEntropy"))
        assertIs<DeeplinkType.CashLink>(type)
        assertEquals("someEntropy", type.entropy)
    }

    @Test
    fun `jump link unwraps to the wrapped login link`() {
        val type = router.classify(jump("https://app.flipcash.com/login/e=abc123"))
        assertIs<DeeplinkType.Login>(type)
        assertEquals("abc123", type.entropy)
    }

    @Test
    fun `jump link preserves an unencoded query string in the wrapped url`() {
        // The producer may leave `&` raw in the fragment; everything after `source=` is the target.
        val type = router.classify(
            DeepLink("https://jump.flipcash.com/#source=https%3A%2F%2Fapp.flipcash.com%2Fverify%3Femail%3Da%40b.com%26code%3D123456")
        )
        assertIs<DeeplinkType.EmailVerification>(type)
        assertEquals("a@b.com", type.email)
        assertEquals("123456", type.code)
    }

    @Test
    fun `jump unwrapping decodes percent escapes only, not plus-as-space`() {
        // A raw `+` in the fragment is a literal plus in the wrapped URL's path. Decoding with
        // URLDecoder (form semantics) would turn it into a space and corrupt the payload;
        // Uri.decode, like iOS's removingPercentEncoding, leaves it alone.
        val type = router.classify(
            DeepLink("https://jump.flipcash.com/#source=https%3A%2F%2Fapp.flipcash.com%2Flogin%2Fe%3Dabc+def")
        )
        assertIs<DeeplinkType.Login>(type)
        assertEquals("abc+def", type.entropy)
    }

    @Test
    fun `jump link dispatches like the wrapped link`() {
        loggedIn()
        val action = router.dispatch(jump("https://app.flipcash.com/token/$MINT"))
        assertIs<DeeplinkAction.OpenToken>(action)
        assertEquals(Mint(MINT), action.mint)
    }

    @Test
    fun `jump link with no source fragment and no path classifies to null`() {
        assertNull(router.classify(DeepLink("https://jump.flipcash.com/")))
        assertNull(router.classify(DeepLink("https://jump.flipcash.com/#other=1")))
        assertNull(router.classify(DeepLink("https://jump.flipcash.com/#source=")))
    }

    @Test
    fun `jump link carrying a route on its own path is classified by that path`() {
        // What the website's tip interstitial navigates to from "Open in Flipcash" — a bare
        // redirector path, no `#source=` fragment.
        val userId = "11111111-1111-1111-1111-111111111111"
        val type = router.classify(DeepLink("https://jump.flipcash.com/tip/$userId"))

        assertIs<DeeplinkType.Tipcard>(type)
        assertEquals(UUID.fromString(userId).bytes, type.userId)
    }

    @Test
    fun `jump link path routes dispatch like the same path on an app host`() {
        loggedIn()
        currentUserId = UUID.fromString("22222222-2222-2222-2222-222222222222").bytes

        val userId = "11111111-1111-1111-1111-111111111111"
        val action = router.dispatch(DeepLink("https://jump.flipcash.com/tip/$userId"))

        assertIs<DeeplinkAction.PresentTipCard>(action)
        assertEquals(TipCardOwner.ById(UUID.fromString(userId).bytes), action.owner)
    }

    @Test
    fun `a source fragment still wins over the jump link's own path`() {
        val type = router.classify(
            DeepLink(
                "https://jump.flipcash.com/tip/11111111-1111-1111-1111-111111111111#source=" +
                    URLEncoder.encode("https://app.flipcash.com/token/$MINT", "UTF-8")
            )
        )
        assertIs<DeeplinkType.TokenInfo>(type)
    }

    @Test
    fun `jump link does not claim a handle the way the bare host does`() {
        // isProfileLink is host-gated: only flipcash.com gives a person a whole path segment,
        // so `jump.flipcash.com/sally` is not a tip card by handle.
        assertNull(router.classify(DeepLink("https://jump.flipcash.com/sally_streamer")))
    }

    @Test
    fun `jump link wrapping an unroutable url classifies to null`() {
        assertNull(router.classify(jump("https://app.flipcash.com/chat/abc")))
        assertNull(router.classify(jump("not a url at all")))
    }

    @Test
    fun `nested jump links are not followed`() {
        val inner = "https://jump.flipcash.com/#source=" +
            URLEncoder.encode("https://app.flipcash.com/token/$MINT", "UTF-8")
        assertNull(router.classify(jump(inner)))
    }

    @Test
    fun `jump host is matched case insensitively`() {
        val type = router.classify(
            DeepLink("https://JUMP.Flipcash.com/#source=" + URLEncoder.encode("https://app.flipcash.com/token/$MINT", "UTF-8"))
        )
        assertIs<DeeplinkType.TokenInfo>(type)
    }

    // endregion
}
