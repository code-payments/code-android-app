package com.flipcash.app.router.internal

import androidx.core.net.toUri
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.navigation.Key
import com.flipcash.app.core.navigation.fragments
import com.flipcash.app.core.phantom.PhantomConnectionResult
import com.flipcash.app.core.phantom.PhantomDeeplinkError
import com.flipcash.app.core.phantom.PhantomDeeplinkOrigin
import com.flipcash.app.core.phantom.PhantomSigningResult
import com.flipcash.app.router.Router
import com.flipcash.app.router.internal.AppRouter.Companion.cashLink
import com.flipcash.app.router.internal.AppRouter.Companion.login
import com.flipcash.app.router.internal.AppRouter.Companion.phantom
import com.flipcash.app.router.internal.AppRouter.Companion.pool
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.decodeBase58
import com.getcode.utils.decodeBase64
import dev.theolm.rinku.DeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

internal class AppRouter(
    private val userManager: UserManager,
) : Router, CoroutineScope by CoroutineScope(Dispatchers.IO) {
    companion object {
        val login = listOf("login")
        val cashLink = listOf("c", "cash")
        val pool = listOf("p", "pool")
        val phantom = listOf("phantom")
    }

    override suspend fun processDestination(deeplink: DeepLink?): List<Screen> {
        return deeplink?.let {
            val type = processType(deeplink) ?: return emptyList()
            when (type) {
                is DeeplinkType.Login -> {
                    if (userManager.authState is AuthState.LoggedInWithUser) {
                        listOf(ScreenRegistry.get(NavScreenProvider.HomeScreen.Scanner(type)))
                    } else {
                        listOf(ScreenRegistry.get(NavScreenProvider.Login.Home(type.entropy, true)))
                    }
                }
                is DeeplinkType.CashLink -> {
                    if (userManager.authState is AuthState.LoggedInWithUser) {
                        listOf(ScreenRegistry.get(NavScreenProvider.HomeScreen.Scanner(type)))
                    } else {
                        listOf(ScreenRegistry.get(NavScreenProvider.Login.Home()))
                    }
                }

                is DeeplinkType.Pool -> {
                    if (userManager.authState is AuthState.LoggedInWithUser) {
                        listOf(ScreenRegistry.get(NavScreenProvider.HomeScreen.Scanner(type)))
                    } else {
                        listOf(ScreenRegistry.get(NavScreenProvider.Login.Home()))
                    }
                }

                is DeeplinkType.PhantomConnection -> {
                    if (userManager.authState is AuthState.LoggedInWithUser) {
                        listOf(ScreenRegistry.get(NavScreenProvider.HomeScreen.Scanner()))
                    } else {
                        listOf(ScreenRegistry.get(NavScreenProvider.Login.Home()))
                    }
                }

                is DeeplinkType.PhantomSignedTransaction ->  {
                    if (userManager.authState is AuthState.LoggedInWithUser) {
                        listOf(ScreenRegistry.get(NavScreenProvider.HomeScreen.Scanner()))
                    } else {
                        listOf(ScreenRegistry.get(NavScreenProvider.Login.Home()))
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
                deeplink.isPool() -> deeplink.handlePoolLink()
                deeplink.isPhantomConnection() -> deeplink.handlePhantomConnect()
                deeplink.isPhantomSignedTransaction() -> deeplink.handlePhantomSignedTransaction()
                else -> null
            }
        }
    }
}

private fun DeepLink.isLogin(): Boolean = login.contains(pathSegments[0])
private fun DeepLink.isCashLink(): Boolean = cashLink.contains(pathSegments[0])
private fun DeepLink.isPool(): Boolean = pool.contains(pathSegments[0])
private fun DeepLink.isPhantomConnection(): Boolean = phantom.contains(pathSegments.getOrNull(0))
        && pathSegments.getOrNull(1) == "connected"

private fun DeepLink.isPhantomSignedTransaction(): Boolean = phantom.contains(pathSegments.getOrNull(0))
        && pathSegments.getOrNull(1) == "signed"

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

private fun DeepLink.handlePoolLink(): DeeplinkType.Pool? {
    val seed = data.toUri().fragments[Key.entropy] ?: return null
    return DeeplinkType.Pool(seed)
}

private fun DeepLink.handlePhantomConnect(): DeeplinkType.PhantomConnection? {
    val uri = data.toUri()
    val origin = uri.getQueryParameter("origin")

    val phantomOrigin = PhantomDeeplinkOrigin.fromString(origin) ?: return null
    val errorCode = uri.getQueryParameter("errorCode")
    val errorMessage = uri.getQueryParameter("errorMessage")

    val encryptionPublicKey = uri.getQueryParameter("phantom_encryption_public_key")?.decodeBase58()?.toList()
    val nonce = uri.getQueryParameter("nonce")?.decodeBase58()?.toList()
    val data = uri.getQueryParameter("data")?.decodeBase58()?.toList()

    val result = if (encryptionPublicKey != null && nonce != null && data != null) {
        PhantomConnectionResult(
            encryptionPublicKey = encryptionPublicKey,
            nonce = nonce,
            encryptedData = data
        )
    } else {
        null
    }

    return DeeplinkType.PhantomConnection(
        origin = phantomOrigin,
        result = result,
        error = if (errorCode != null && errorMessage != null) {
            PhantomDeeplinkError(errorCode, errorMessage)
        } else null,
    )
}

private fun DeepLink.handlePhantomSignedTransaction(): DeeplinkType.PhantomSignedTransaction? {
    val uri = data.toUri()
    val origin = uri.getQueryParameter("origin")

    val phantomOrigin = PhantomDeeplinkOrigin.fromString(origin) ?: return null
    val errorCode = uri.getQueryParameter("errorCode")
    val errorMessage = uri.getQueryParameter("errorMessage")
    val nonce = uri.getQueryParameter("nonce")?.decodeBase58()?.toList()
    val data = uri.getQueryParameter("data")?.decodeBase58()?.toList()

    val result = if (nonce != null && data != null) {
        PhantomSigningResult(
            nonce = nonce,
            encryptedData = data
        )
    } else {
        null
    }

    return DeeplinkType.PhantomSignedTransaction(
        origin = phantomOrigin,
        result = result,
        error = if (errorCode != null && errorMessage != null) {
            PhantomDeeplinkError(errorCode, errorMessage)
        } else null,
    )
}