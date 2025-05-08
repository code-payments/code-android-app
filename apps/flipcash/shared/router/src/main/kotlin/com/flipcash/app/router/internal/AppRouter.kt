package com.flipcash.app.router.internal

import androidx.core.net.toUri
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.navigation.Key
import com.flipcash.app.core.navigation.fragments
import com.flipcash.app.router.Router
import dev.theolm.rinku.DeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

internal class AppRouter : Router, CoroutineScope by CoroutineScope(Dispatchers.IO) {
    companion object {
        val login = listOf("login")
        val cashLink = listOf("c", "cash")
    }

    override suspend fun processDestination(deeplink: DeepLink?): List<Screen> {
        return deeplink?.let {
            val type = processType(deeplink) ?: return emptyList()
            when (type) {
                is DeeplinkType.Login -> listOf(ScreenRegistry.get(NavScreenProvider.HomeScreen.Scanner(type)))
                is DeeplinkType.CashLink -> listOf(ScreenRegistry.get(NavScreenProvider.HomeScreen.Scanner(type)))
            }
        } ?: emptyList()
    }

    override fun processType(deeplink: DeepLink?): DeeplinkType? {
        return deeplink?.let {
            when (deeplink.pathSegments.size) {
                1 -> {
                    when {
                        login.contains(deeplink.pathSegments[0]) -> {
                            val uri = deeplink.data.toUri()
                            var entropy = uri.fragments[Key.entropy]
                            if (entropy == null) {
                                entropy = uri.getQueryParameter("data")
                            }

                            entropy ?: return null

                            DeeplinkType.Login(entropy)
                        }

                        cashLink.contains(deeplink.pathSegments[0]) -> {
                            val entropy = deeplink.data.toUri().fragments[Key.entropy] ?: return null

                            DeeplinkType.CashLink(entropy)
                        }

                        else -> null
                    }
                }
                else -> null
            }
        }
    }
}