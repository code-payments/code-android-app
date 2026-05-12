package com.flipcash.app.router.internal

import androidx.core.net.toUri
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.navigation.Key
import com.flipcash.app.core.navigation.fragments
import com.flipcash.app.core.onramp.deeplinks.ExternalWalletDeeplinkError
import com.flipcash.app.core.onramp.deeplinks.OnRampDeeplinkOrigin
import com.flipcash.app.core.onramp.deeplinks.WalletDeeplinkConnectionResult
import com.flipcash.app.core.onramp.deeplinks.WalletDeeplinkSigningResult
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.verification.email.EmailDeeplinkOrigin
import com.flipcash.app.router.Router
import com.flipcash.app.router.internal.AppRouter.Companion.cashLink
import com.flipcash.app.router.internal.AppRouter.Companion.external
import com.flipcash.app.router.internal.AppRouter.Companion.login
import com.flipcash.app.router.internal.AppRouter.Companion.token
import com.flipcash.app.router.internal.AppRouter.Companion.verification
import com.flipcash.services.user.AuthState
import com.getcode.solana.keys.Mint
import com.getcode.utils.decodeBase58
import com.getcode.utils.decodeBase64
import com.getcode.utils.urlDecode
import dev.theolm.rinku.DeepLink
import org.json.JSONObject

internal class AppRouter(
    private val authStateProvider: () -> AuthState,
) : Router {
    companion object {
        val login = listOf("login")
        val cashLink = listOf("c", "cash")
        val external = listOf("external")
        val verification = listOf("verify")
        val token = listOf("token")
    }

    override fun dispatch(deepLink: DeepLink): DeeplinkAction {
        val type = classify(deepLink) ?: return DeeplinkAction.None

        // Not logged in — redirect to login (or login deeplink itself)
        if (authStateProvider() !is AuthState.LoggedInWithUser) {
            return when (type) {
                is DeeplinkType.Login -> DeeplinkAction.Navigate(
                    listOf(AppRoute.Onboarding.Login(type.entropy, fromDeeplink = true))
                )
                else -> DeeplinkAction.Navigate(listOf(AppRoute.Onboarding.Login()))
            }
        }

        // Logged in — resolve action
        return when (type) {
            is DeeplinkType.Login -> DeeplinkAction.Login(type.entropy)

            is DeeplinkType.CashLink -> DeeplinkAction.OpenCashLink(type.entropy)

            is DeeplinkType.TokenInfo -> DeeplinkAction.Navigate(
                listOf(AppRoute.Sheets.Wallet, AppRoute.Token.Info(type.mint, fromDeeplink = true))
            )

            is DeeplinkType.ExternalWalletConnection,
            is DeeplinkType.ExternalWalletSignedTransaction ->
                DeeplinkAction.ExternalWallet(type)

            is DeeplinkType.EmailVerification -> resolveEmailVerification(type)
        }
    }

    override fun classify(deepLink: DeepLink): DeeplinkType? {
        return when {
            deepLink.isLogin() -> deepLink.handleLoginLink()
            deepLink.isCashLink() -> deepLink.handleCashLink()
            deepLink.isToken() -> deepLink.handleTokenLink()
            deepLink.isExternalWalletConnection() -> deepLink.handleWalletConnect()
            deepLink.isExternalWalletSignedTransaction() -> deepLink.handleWalletSignedTransaction()
            deepLink.isEmailVerification() -> deepLink.handleEmailVerification()
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

private fun DeepLink.isExternalWalletConnection(): Boolean = external.contains(pathSegments.getOrNull(0))
        && pathSegments.getOrNull(2) == "connected"

private fun DeepLink.isExternalWalletSignedTransaction(): Boolean = external.contains(pathSegments.getOrNull(0))
        && pathSegments.getOrNull(2) == "signed"

private fun DeepLink.isEmailVerification(): Boolean = verification.contains(pathSegments.getOrNull(0))
        && data.toUri().getQueryParameter("email") != null

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

private fun DeepLink.handleWalletConnect(): DeeplinkType.ExternalWalletConnection? {
    val uri = data.toUri()
    val wallet = uri.pathSegments.getOrNull(1) ?: return null
    val origin = uri.getQueryParameter("origin")

    val phantomOrigin = OnRampDeeplinkOrigin.fromString(origin) ?: return null
    val errorCode = uri.getQueryParameter("errorCode")
    val errorMessage = uri.getQueryParameter("errorMessage")

    val encryptionPublicKey = uri.getQueryParameter("${wallet}_encryption_public_key")?.decodeBase58()?.toList()
    val nonce = uri.getQueryParameter("nonce")?.decodeBase58()?.toList()
    val data = uri.getQueryParameter("data")?.decodeBase58()?.toList()

    val result = if (encryptionPublicKey != null && nonce != null && data != null) {
        WalletDeeplinkConnectionResult(
            encryptionPublicKey = encryptionPublicKey,
            nonce = nonce,
            encryptedData = data
        )
    } else {
        null
    }

    return DeeplinkType.ExternalWalletConnection(
        origin = phantomOrigin,
        result = result,
        error = if (errorCode != null && errorMessage != null) {
            ExternalWalletDeeplinkError(errorCode, errorMessage)
        } else null,
    )
}

private fun DeepLink.handleWalletSignedTransaction(): DeeplinkType.ExternalWalletSignedTransaction? {
    val uri = data.toUri()
    val origin = uri.getQueryParameter("origin")

    val phantomOrigin = OnRampDeeplinkOrigin.fromString(origin) ?: return null
    val errorCode = uri.getQueryParameter("errorCode")
    val errorMessage = uri.getQueryParameter("errorMessage")
    val nonce = uri.getQueryParameter("nonce")?.decodeBase58()?.toList()
    val data = uri.getQueryParameter("data")?.decodeBase58()?.toList()

    val result = if (nonce != null && data != null) {
        WalletDeeplinkSigningResult(
            nonce = nonce,
            encryptedData = data
        )
    } else {
        null
    }

    return DeeplinkType.ExternalWalletSignedTransaction(
        origin = phantomOrigin,
        result = result,
        error = if (errorCode != null && errorMessage != null) {
            ExternalWalletDeeplinkError(errorCode, errorMessage)
        } else null,
    )
}

//  https://app.flipcash.com/verify?email={email}&code={code}&client_data={data}
private fun DeepLink.handleEmailVerification(): DeeplinkType.EmailVerification? {
    val uri = data.toUri()
    val email = uri.getQueryParameter("email")?.urlDecode()
    val code = uri.getQueryParameter("code")
    val clientData = uri.getQueryParameter("client_data")?.urlDecode()
        ?.let { JSONObject(it) }

    val origin = clientData?.getString("origin")?.decodeBase64()?.let { String(it, Charsets.UTF_8) }
    if (code != null && email != null) {
        return DeeplinkType.EmailVerification(
            email = email,
            code = code,
            origin = origin,
        )
    }

    return null
}
