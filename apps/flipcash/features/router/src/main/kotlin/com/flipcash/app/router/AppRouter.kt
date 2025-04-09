package com.flipcash.app.router

import androidx.core.net.toUri
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import com.flipcash.app.core.NavScreenProvider
import dev.theolm.rinku.DeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class AppRouter : Router, CoroutineScope by CoroutineScope(Dispatchers.IO) {
    companion object {
        val login = listOf("login")
    }

    override suspend fun processDestination(deeplink: DeepLink?): List<Screen> {
        return deeplink?.let {
            val type = processType(deeplink) ?: return emptyList()
            when (type) {
                is DeeplinkType.Login -> listOf(ScreenRegistry.get(NavScreenProvider.HomeScreen.Scanner(deeplink)))
            }
        } ?: emptyList()
    }

    override fun processType(deeplink: DeepLink?): DeeplinkType? {
        return deeplink?.let {
            when (deeplink.pathSegments.size) {
                1 -> {
                    when {
                        login.contains(deeplink.pathSegments[0]) -> {
                            var entropy = runCatching {
                                deeplink.data.toUri().getQueryParameter("data")
                            }.getOrNull()

                            // if not found at data check `e`
                            if (entropy == null) {
                                entropy = runCatching {
                                    deeplink.data.toUri().getQueryParameter("e")
                                }.getOrNull() ?: return null
                            }

                            DeeplinkType.Login(entropy)
                        }

                        else -> null
                    }
                }

                else -> null
            }
        }
    }
}