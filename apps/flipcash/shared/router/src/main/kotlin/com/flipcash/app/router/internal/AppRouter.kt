package com.flipcash.app.router.internal

import android.net.Uri
import androidx.core.net.toUri
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.isUsernameShaped
import com.flipcash.app.core.navigation.Key
import com.flipcash.app.core.navigation.fragments
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.verification.email.EmailDeeplinkOrigin
import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.app.router.Router
import com.flipcash.app.router.internal.AppRouter.Companion.cashLink
import com.flipcash.app.router.internal.AppRouter.Companion.chat
import com.flipcash.app.router.internal.AppRouter.Companion.login
import com.flipcash.app.router.internal.AppRouter.Companion.tip
import com.flipcash.app.router.internal.AppRouter.Companion.token
import com.flipcash.app.router.internal.AppRouter.Companion.verification
import com.flipcash.services.user.AuthState
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.bytes
import com.getcode.solana.keys.Mint
import com.getcode.utils.TraceType
import com.getcode.utils.decodeBase64
import com.getcode.utils.decodeBase64UrlSafe
import com.getcode.utils.trace
import dev.theolm.rinku.DeepLink
import org.json.JSONObject
import java.util.UUID

internal class AppRouter(
    private val authStateProvider: () -> AuthState,
    private val currentUserProvider: () -> CurrentUser,
) : Router {

    /**
     * How the signed-in account can be addressed, for matching a tip card link against itself.
     * Both nullable and read together: see [TipCardOwner.isSelf] for why the id and the handle
     * cannot be sourced from one profile.
     */
    internal data class CurrentUser(val id: ID?, val username: String?)

    companion object {
        val login = listOf("login")
        val cashLink = listOf("c", "cash")
        val verification = listOf("verify")
        val token = listOf("token")
        val chat = listOf("chat")
        val tip = listOf("tip")

        /**
         * Redirector host. It carries no route of its own — the real link is percent-encoded in
         * the fragment as `#source=<url>`. See [DeepLink.unwrapJumpTarget].
         */
        const val JUMP_HOST = "jump.flipcash.com"

        /**
         * The bare host, which serves the website *and* every user's vanity profile link
         * (`flipcash.com/sally_streamer`). Distinct from the `app.` / `send.` hosts, whose whole
         * path space belongs to the app.
         */
        const val VANITY_HOST = "flipcash.com"

        /**
         * Paths on [VANITY_HOST] that belong to the website rather than to a person. Every one of
         * them is charset-valid as a username, and the server reserves them — so a link to one
         * could only ever fail to resolve, but it would fail *inside* the app, having taken the tap
         * away from the browser. Ruling them out here keeps `flipcash.com/download` a web link.
         *
         * This is the whole of the narrowing. An intent filter can only widen — its `data` elements
         * OR together and there is no exclude form — so the manifest's `pathAdvancedPattern` can
         * hold the claim to the handle *shape* but not subtract these particular words from it.
         * The AASA's `exclude` entries are how iOS says the same thing, which is why this list is
         * kept in step with them: that file is the website's own statement of what it serves.
         * Only its handle-shaped entries appear here — it also excludes paths the filter could
         * never match (`/favicon.ico`, `/robots.txt`, anything multi-segment).
         */
        val reservedVanityPaths: Set<String> =
            // The website's own pages.
            setOf(
                "download", "privacy", "terms", "support", "help", "about", "blog", "legal",
                "currencycreator",
            ) +
                // Static roots and the web API, served off the apex alongside the pages.
                setOf("app", "api", "assets", "fonts", "icons", "js", "v1") +
                // Routes belonging to the app hosts. The apex answers for them too, so a link
                // naming one is a mis-hosted route, not somebody's handle.
                setOf("pool", "wallet") +
                login + cashLink + verification + token + chat + tip
    }

    override fun dispatch(deepLink: DeepLink): DeeplinkAction {
        val type = classify(deepLink) ?: return deepLink.unrouted()

        // Not logged in — redirect to login (or login deeplink itself)
        if (authStateProvider() !is AuthState.Ready) {
            return when (type) {
                is DeeplinkType.Login -> DeeplinkAction.Navigate(
                    listOf(AppRoute.OnboardingFlow(seed = type.entropy, fromDeeplink = true))
                )
                else -> DeeplinkAction.Navigate(listOf(AppRoute.OnboardingFlow()))
            }
        }

        // Logged in — resolve action
        return when (type) {
            is DeeplinkType.Login -> DeeplinkAction.Login(type.entropy)

            is DeeplinkType.CashLink -> DeeplinkAction.OpenCashLink(type.entropy)

            // Not a plain Navigate: under v2 a token link lands as the wallet's expanded card, not
            // as a pushed screen. The route form is carried along for v1, which has no expansion.
            // See DeeplinkAction.OpenToken.
            is DeeplinkType.TokenInfo -> DeeplinkAction.OpenToken(
                mint = type.mint,
                routes = listOf(
                    AppRoute.Sheets.Wallet,
                    AppRoute.Token.Info(type.mint, fromDeeplink = true),
                ),
            )

            is DeeplinkType.EmailVerification -> resolveEmailVerification(type)

            is DeeplinkType.TipChat -> DeeplinkAction.Navigate(
                listOf(AppRoute.Sheets.Tips(), AppRoute.Messaging.Chat(type.identifier))
            )

            is DeeplinkType.Tipcard -> tipCard(TipCardOwner.ById(type.userId))

            is DeeplinkType.TipcardByUsername -> tipCard(TipCardOwner.ByUsername(type.username))
        }
    }

    /**
     * Where a tip card link goes. Your own leads nowhere payable, so instead of presenting a card
     * that can't be acted on it lands on the You tab — the surface that owns your tip card (see
     * NavBarRoutes: NavBarButton.TipCard -> Sheets.Menu).
     *
     * The self-check belongs here and not after resolution: the session announces a self-tip
     * through a replay-less event that only the scanner collects, so a link opened onto any other
     * tab — a cold start lands on the wallet — would drop it and do nothing at all.
     */
    private fun tipCard(owner: TipCardOwner): DeeplinkAction {
        val self = currentUserProvider()
        return if (owner.isSelf(self.id, self.username)) {
            DeeplinkAction.Navigate(listOf(AppRoute.Sheets.Menu))
        } else {
            DeeplinkAction.PresentTipCard(owner)
        }
    }

    override fun classify(deepLink: DeepLink): DeeplinkType? {
        // Deeplink payloads are attacker-controllable (every handled host is `autoVerify`) and both
        // callers of dispatch/classify run where a throw is fatal — inside composition (MainRoot)
        // and inside a LaunchedEffect (App). A parse failure must degrade to "no deeplink", loudly
        // in logs, never to a crash.
        return runCatching { classifyOrThrow(deepLink) }
            .onFailure {
                trace(
                    tag = "AppRouter",
                    message = "Failed to classify deeplink; ignoring it",
                    error = it,
                    type = TraceType.Error,
                )
            }
            .getOrNull()
    }

    private fun classifyOrThrow(deepLink: DeepLink): DeeplinkType? {
        return when {
            // A jump link is a wrapper, never a destination. Unwrap once and classify the inner
            // URL; a jump pointing at another jump is malformed and drops to null rather than
            // recursing. Mirrors iOS DeepLinkController.
            deepLink.isJump() -> deepLink.unwrapJumpTarget()
                ?.takeUnless { it.isJump() }
                ?.let { classifyOrThrow(it) }
            deepLink.isLogin() -> deepLink.handleLoginLink()
            deepLink.isCashLink() -> deepLink.handleCashLink()
            deepLink.isToken() -> deepLink.handleTokenLink()
            deepLink.isEmailVerification() -> deepLink.handleEmailVerification()
            deepLink.isTipChat() -> deepLink.handleTipChat()
            deepLink.isTipCard() -> deepLink.handleTipCard()
            deepLink.isVanityProfile() -> deepLink.handleVanityProfile()
            // `/chat/{id}` links are intentionally NOT handled: the Send tab / direct-send
            // flow they opened was removed. The manifest no longer claims that path either, so
            // such a link opens in the browser rather than dead-ending here. Re-add routing and
            // the App Link filter together, or not at all.
            // (Tip DMs use `/tip/chat/{id}` — handled above via isTipChat.)
            else -> null
        }
    }

    /**
     * What to do with a link the app was handed but has no route for.
     *
     * For every host but the bare one that is "nothing": those hosts belong to the app, and a path
     * it doesn't know is a link it was never meant to receive. `flipcash.com` is different — its
     * path space is shared with the website, and the App Link filter can only narrow it to the
     * handle charset, which `/download` and `/privacy` also satisfy (and which the platform ignores
     * below API 31). Send those back to a browser instead of dead-ending on the home screen.
     */
    private fun DeepLink.unrouted(): DeeplinkAction =
        if (host.removePrefix("www.").equals(VANITY_HOST, ignoreCase = true)) {
            DeeplinkAction.OpenExternally(data)
        } else {
            DeeplinkAction.None
        }

    private fun resolveEmailVerification(type: DeeplinkType.EmailVerification): DeeplinkAction {
        val origin = EmailDeeplinkOrigin.deserialize(type.origin.orEmpty())
        val routes: List<AppRoute> = when (origin) {
            is EmailDeeplinkOrigin.OnRamp -> when (val source = origin.source) {
                is AppRoute.Token.Swap -> {
                    val mint = (source.purpose as? SwapPurpose.Buy)?.mint
                        ?: return DeeplinkAction.None
                    listOf(
                        AppRoute.Token.Info(mint),
                        AppRoute.Token.Swap(SwapPurpose.Buy(mint)),
                    ) + AppRoute.Verification(
                        origin = AppRoute.Token.Swap(SwapPurpose.Buy(mint)),
                        includePhone = false,
                        email = type.email,
                        emailVerificationCode = type.code
                    )
                }
                else -> emptyList()
            }

            EmailDeeplinkOrigin.MyAccount ->
                listOf(
                    AppRoute.Sheets.Menu,
                    AppRoute.Menu.MyAccount
                ) + AppRoute.Verification(
                    origin = AppRoute.Menu.MyAccount,
                    includePhone = false,
                    email = type.email,
                    emailVerificationCode = type.code
                )

            null -> emptyList()
        }

        return if (routes.isNotEmpty()) {
            DeeplinkAction.Navigate(routes)
        } else {
            DeeplinkAction.None
        }
    }
}

private fun DeepLink.isJump(): Boolean = host.equals(AppRouter.JUMP_HOST, ignoreCase = true)

/**
 * `jump.flipcash.com/#source=<percent-encoded url>` is a redirector used by the web app to hand a
 * link off to the native app. Pull the wrapped URL back out so it can be classified as if it had
 * arrived directly. Returns null when the fragment is absent, empty, or not a parseable URL.
 */
private fun DeepLink.unwrapJumpTarget(): DeepLink? {
    val fragment = data.substringAfter('#', missingDelimiterValue = "")
    if (!fragment.startsWith(JUMP_SOURCE_PARAM)) return null

    // Everything after `source=`, not up to the next `&` — the wrapped URL may carry its own
    // query string with `&` separators that the producer left unencoded. iOS does the same.
    val encoded = fragment.removePrefix(JUMP_SOURCE_PARAM).takeIf { it.isNotBlank() } ?: return null

    // Uri.decode, not URLDecoder: the payload is a URL, and `+` in it (a `user+tag@` email in a
    // /verify query, say) is a literal plus, not a space.
    val target = Uri.decode(encoded)?.takeIf { it.isNotBlank() } ?: return null

    return runCatching { DeepLink(target) }.getOrNull()
}

private const val JUMP_SOURCE_PARAM = "source="

private fun DeepLink.isLogin(): Boolean = login.contains(pathSegments.getOrNull(0))
private fun DeepLink.isCashLink(): Boolean = cashLink.contains(pathSegments.getOrNull(0))
private fun DeepLink.isToken(): Boolean = token.contains(pathSegments.getOrNull(0))

private fun DeepLink.isEmailVerification(): Boolean = verification.contains(pathSegments.getOrNull(0))
        && data.toUri().getQueryParameter("email") != null

// https://app.flipcash.com/tip/chat/{url encoded chatId}
private fun DeepLink.isTipChat(): Boolean =
    tip.contains(pathSegments.getOrNull(0)) && chat.contains(pathSegments.getOrNull(1))

private fun DeepLink.isTipCard(): Boolean =  tip.contains(pathSegments.getOrNull(0))

/**
 * `flipcash.com/{username}` — a single path segment on the bare host, shaped like a handle and not
 * one of the website's own pages.
 *
 * All three conditions matter. The manifest claims this host for username-shaped paths only, but a
 * `pathAdvancedPattern` is ignored below API 31, so on those versions the whole host arrives here
 * and this is the only place the distinction is made.
 */
private fun DeepLink.isVanityProfile(): Boolean {
    if (!host.removePrefix("www.").equals(AppRouter.VANITY_HOST, ignoreCase = true)) return false
    val segment = pathSegments.singleOrNull()?.lowercase() ?: return false
    return segment.isUsernameShaped() && segment !in AppRouter.reservedVanityPaths
}

private fun DeepLink.handleVanityProfile(): DeeplinkType.TipcardByUsername? {
    // Lowercased, not just matched case-insensitively: handles are lowercase on the wire, and this
    // string is what the profile lookup is keyed by.
    val username = pathSegments.singleOrNull()?.lowercase() ?: return null
    return DeeplinkType.TipcardByUsername(username)
}

private fun DeepLink.handleLoginLink(): DeeplinkType.Login? {
    val uri = data.toUri()
    var entropy = uri.fragments[Key.entropy]
    if (entropy == null) {
        entropy = uri.getQueryParameter("data")
    }

    entropy ?: return null

    return DeeplinkType.Login(entropy)
}

private fun DeepLink.handleCashLink(): DeeplinkType.CashLink? {
    val entropy = data.toUri().fragments[Key.entropy] ?: return null

    return DeeplinkType.CashLink(entropy)
}

private fun DeepLink.handleTokenLink(): DeeplinkType.TokenInfo? {
    val uri = data.toUri()
    val mint = uri.pathSegments.getOrNull(1) ?: return null
    return DeeplinkType.TokenInfo(Mint(mint))
}

// https://app.flipcash.com/tip/chat/{url encoded chatId}
private fun DeepLink.handleTipChat(): DeeplinkType.TipChat? {
    val uri = data.toUri()
    // Tip chats are always addressed by canonical chat id (base64 url-safe bytes),
    // never by phone number, so there is no ByContact branch here.
    val chatTarget = uri.pathSegments.getOrNull(2) ?: return null
    val chatId = ChatId(chatTarget.decodeBase64UrlSafe().toList())

    return DeeplinkType.TipChat(ChatIdentifier.ByChatId(chatId))
}

private fun DeepLink.handleTipCard(): DeeplinkType.Tipcard? {
    val uri = data.toUri()
    val userId = uri.pathSegments.getOrNull(1)
        ?.let { runCatching { UUID.fromString(it).bytes }.getOrNull() }
        ?: return null

    return DeeplinkType.Tipcard(userId)
}

//  https://app.flipcash.com/verify?email={email}&code={code}&client_data={data}
private fun DeepLink.handleEmailVerification(): DeeplinkType.EmailVerification? {
    val uri = data.toUri()
    // No urlDecode here: getQueryParameter already percent-decodes. Decoding twice mangles a
    // plus-tagged address (`user%2Btag@` -> `user tag@`) and a base64 client_data payload (`+`
    // is in the standard alphabet), and URLDecoder throws outright once a `%25` has become a
    // bare `%` -- which would drop the whole link.
    val email = uri.getQueryParameter("email")
    val code = uri.getQueryParameter("code")
    // client_data is optional and untrusted: a malformed or absent payload just means "no origin",
    // which resolveEmailVerification already handles. Never let it fail the whole link.
    val clientData = uri.getQueryParameter("client_data")
        ?.let { runCatching { JSONObject(it) }.getOrNull() }

    val origin = clientData?.optString("origin")
        ?.takeIf { it.isNotEmpty() }
        ?.let { encoded ->
            runCatching { String(encoded.decodeBase64(), Charsets.UTF_8) }.getOrNull()
        }
    if (code != null && email != null) {
        return DeeplinkType.EmailVerification(
            email = email,
            code = code,
            origin = origin,
        )
    }

    return null
}
