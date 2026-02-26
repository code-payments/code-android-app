package com.flipcash.app.router.internal

import androidx.core.net.toUri
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.navigation.Key
import com.flipcash.app.core.navigation.fragments
import com.flipcash.app.core.onramp.deeplinks.ExternalWalletDeeplinkError
import com.flipcash.app.core.onramp.deeplinks.OnRampDeeplinkOrigin
import com.flipcash.app.core.onramp.deeplinks.WalletDeeplinkConnectionResult
import com.flipcash.app.core.onramp.deeplinks.WalletDeeplinkSigningResult
import com.flipcash.app.router.Router
import com.flipcash.app.router.internal.AppRouter.Companion.cashLink
import com.flipcash.app.router.internal.AppRouter.Companion.external
import com.flipcash.app.router.internal.AppRouter.Companion.login
import com.flipcash.app.router.internal.AppRouter.Companion.token
import com.flipcash.app.router.internal.AppRouter.Companion.verification
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.solana.keys.Mint
import com.getcode.utils.decodeBase58
import com.getcode.utils.decodeBase64
import com.getcode.utils.urlDecode
import dev.theolm.rinku.DeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject

internal class AppRouter(
    private val userManager: UserManager,
) : Router, CoroutineScope by CoroutineScope(Dispatchers.IO) {
    companion object {
        val login = listOf("login")
        val cashLink = listOf("c", "cash")
        val external = listOf("external")
        val verification = listOf("verify")
        val token = listOf("token")
    }

    override suspend fun processDestination(deeplink: DeepLink?): List<Screen> {
        return deeplink?.let {
            val type = processType(deeplink) ?: return emptyList()
            when (type) {
                is DeeplinkType.Login -> {
                    if (userManager.authState is AuthState.LoggedInWithUser) {
                        listOf(ScreenRegistry.get(AppRoute.Main.Scanner(type)))
                    } else {
                        listOf(ScreenRegistry.get(AppRoute.Onboarding.Login(type.entropy, true)))
                    }
                }
                is DeeplinkType.CashLink -> {
                    if (userManager.authState is AuthState.LoggedInWithUser) {
                        listOf(ScreenRegistry.get(AppRoute.Main.Scanner(type)))
                    } else {
                        listOf(ScreenRegistry.get(AppRoute.Onboarding.Login()))
                    }
                }

                is DeeplinkType.ExternalWalletConnection -> {
                    if (userManager.authState is AuthState.LoggedInWithUser) {
                        listOf(ScreenRegistry.get(AppRoute.Main.Scanner()))
                    } else {
                        listOf(ScreenRegistry.get(AppRoute.Onboarding.Login()))
                    }
                }

                is DeeplinkType.ExternalWalletSignedTransaction ->  {
                    if (userManager.authState is AuthState.LoggedInWithUser) {
                        listOf(ScreenRegistry.get(AppRoute.Main.Scanner()))
                    } else {
                        listOf(ScreenRegistry.get(AppRoute.Onboarding.Login()))
                    }
                }

                is DeeplinkType.EmailVerification -> {
                    if (userManager.authState is AuthState.LoggedInWithUser) {
                        listOf(ScreenRegistry.get(AppRoute.Main.Scanner(type)))
                    } else {
                        listOf(ScreenRegistry.get(AppRoute.Onboarding.Login()))
                    }
                }

                is DeeplinkType.TokenInfo -> {
                    if (userManager.authState is AuthState.LoggedInWithUser) {
                        listOf(ScreenRegistry.get(AppRoute.Main.Scanner(type)))
                    } else {
                        listOf(ScreenRegistry.get(AppRoute.Onboarding.Login()))
                    }
                }
            }
        }.orEmpty()
    }

    override fun processType(deeplink: DeepLink?): DeeplinkType? {
        return deeplink?.let {
            when {
                deeplink.isLogin() -> deeplink.handleLoginLink()
                deeplink.isCashLink() -> deeplink.handleCashLink()
                deeplink.isToken() -> deeplink.handleTokenLink()
                deeplink.isExternalWalletConnection() -> deeplink.handleWalletConnect()
                deeplink.isExternalWalletSignedTransaction() -> deeplink.handleWalletSignedTransaction()
                deeplink.isEmailVerification() -> deeplink.handleEmailVerification()
                else -> null
            }
        }
    }
}

private fun DeepLink.isLogin(): Boolean = login.contains(pathSegments[0])
private fun DeepLink.isCashLink(): Boolean = cashLink.contains(pathSegments[0])
private fun DeepLink.isToken(): Boolean = token.contains(pathSegments[0])

private fun DeepLink.isExternalWalletConnection(): Boolean = external.contains(pathSegments.getOrNull(0))
        && pathSegments.getOrNull(2) == "connected"

private fun DeepLink.isExternalWalletSignedTransaction(): Boolean = external.contains(pathSegments.getOrNull(0))
        && pathSegments.getOrNull(2) == "signed"

private fun DeepLink.isEmailVerification(): Boolean = verification.contains(pathSegments[0])
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
    val mint = uri.pathSegments[1]
    return DeeplinkType.TokenInfo(Mint(mint))
}

private fun DeepLink.handleWalletConnect(): DeeplinkType.ExternalWalletConnection? {
    val uri = data.toUri()
    val wallet = uri.pathSegments[1] ?: return null
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