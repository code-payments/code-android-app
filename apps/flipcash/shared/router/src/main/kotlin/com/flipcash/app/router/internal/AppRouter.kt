package com.flipcash.app.router.internal

import androidx.core.net.toUri
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.services.models.chat.ChatId
import com.flipcash.app.core.navigation.Key
import com.flipcash.app.core.navigation.fragments
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.verification.email.EmailDeeplinkOrigin
import com.flipcash.app.router.Router
import com.flipcash.app.router.internal.AppRouter.Companion.cashLink
import com.flipcash.app.router.internal.AppRouter.Companion.chat
import com.flipcash.app.router.internal.AppRouter.Companion.login
import com.flipcash.app.router.internal.AppRouter.Companion.token
import com.flipcash.app.router.internal.AppRouter.Companion.verification
import com.flipcash.services.user.AuthState
import com.getcode.solana.keys.Mint
import com.getcode.utils.decodeBase64
import com.getcode.utils.decodeBase64UrlSafe
import com.getcode.utils.urlDecode
import dev.theolm.rinku.DeepLink
import org.json.JSONObject

internal class AppRouter(
    private val authStateProvider: () -> AuthState,
) : Router {
    companion object {
        val login = listOf("login")
        val cashLink = listOf("c", "cash")
        val verification = listOf("verify")
        val token = listOf("token")
        val chat = listOf("chat")
    }

    override fun dispatch(deepLink: DeepLink): DeeplinkAction {
        val type = classify(deepLink) ?: return DeeplinkAction.None

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

            is DeeplinkType.TokenInfo -> DeeplinkAction.Navigate(
                listOf(AppRoute.Sheets.Wallet, AppRoute.Token.Info(type.mint, fromDeeplink = true))
            )

            is DeeplinkType.EmailVerification -> resolveEmailVerification(type)
            is DeeplinkType.Chat -> DeeplinkAction.Navigate(
                listOf(AppRoute.Sheets.Send(), AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(type.chatId)))
            )
        }
    }

    override fun classify(deepLink: DeepLink): DeeplinkType? {
        return when {
            deepLink.isLogin() -> deepLink.handleLoginLink()
            deepLink.isCashLink() -> deepLink.handleCashLink()
            deepLink.isToken() -> deepLink.handleTokenLink()
            deepLink.isEmailVerification() -> deepLink.handleEmailVerification()
            deepLink.isChat() -> deepLink.handleChat()
            else -> null
        }
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

private fun DeepLink.isLogin(): Boolean = login.contains(pathSegments.getOrNull(0))
private fun DeepLink.isCashLink(): Boolean = cashLink.contains(pathSegments.getOrNull(0))
private fun DeepLink.isToken(): Boolean = token.contains(pathSegments.getOrNull(0))

private fun DeepLink.isEmailVerification(): Boolean = verification.contains(pathSegments.getOrNull(0))
        && data.toUri().getQueryParameter("email") != null

private fun DeepLink.isChat(): Boolean = chat.contains(pathSegments.getOrNull(0))

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

private fun DeepLink.handleChat(): DeeplinkType.Chat? {
    val uri = data.toUri()
    val chatIdBase64 = uri.pathSegments.getOrNull(1) ?: return null
    val chatId = ChatId(chatIdBase64.decodeBase64UrlSafe().toList())
    return DeeplinkType.Chat(chatId)
}

//  https://app.flipcash.com/verify?email={email}&code={code}&client_data={data}
private fun DeepLink.handleEmailVerification(): DeeplinkType.EmailVerification? {
    val uri = data.toUri()
    val email = uri.getQueryParameter("email")?.urlDecode()
    val code = uri.getQueryParameter("code")
    val clientData = uri.getQueryParameter("client_data")?.urlDecode()
        ?.let { JSONObject(it) }

    val origin = clientData?.getString("origin")?.decodeBase64()?.let {
        String(it, Charsets.UTF_8)
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
