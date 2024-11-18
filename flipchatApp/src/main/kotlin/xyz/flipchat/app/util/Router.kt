package xyz.flipchat.app.util

import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.screens.ChildNavTab
import com.getcode.vendor.Base58
import dev.theolm.rinku.DeepLink

interface Router {
    val rootTabs: List<ChildNavTab>
    fun getInitialTabIndex(deeplink: DeepLink?): Int
    fun processDestination(deeplink: DeepLink?): List<Screen>
    fun tabForIndex(index: Int): FcTab
}

// chat/{id}

enum class FcTab {
    Chat, Cash, Settings
}

class RouterImpl(
    override val rootTabs: List<ChildNavTab>,
    private val tabIndexResolver: (FcTab) -> Int,
    private val indexTabResolver: (Int) -> FcTab,
): Router {
    companion object {
        val chats = listOf("chats")
        val cash = listOf("cash")
        val settings = listOf("settings")

        val chatById = listOf("chat")
    }

    override fun tabForIndex(index: Int) = indexTabResolver(index)

    override fun getInitialTabIndex(deeplink: DeepLink?): Int {
        return deeplink?.let {
            when {
                deeplink.pathSegments.isEmpty() -> tabIndexResolver(FcTab.Chat)
                chats.contains(deeplink.pathSegments[0]) -> tabIndexResolver(FcTab.Chat)
                cash.contains(deeplink.pathSegments[0]) -> tabIndexResolver(FcTab.Cash)
                settings.contains(deeplink.pathSegments[0]) -> tabIndexResolver(FcTab.Settings)
                else -> 0
            }
        } ?: 0
    }

    override fun processDestination(deeplink: DeepLink?): List<Screen> {
        return deeplink?.let {
            when (deeplink.pathSegments.size) {
                1 -> emptyList()
                2 -> {
                    when {
                        chatById.contains(deeplink.pathSegments[0]) -> {
                            val chatId = Base58.decode(deeplink.pathSegments[1]).toList()
                            listOf(
                                ScreenRegistry.get(NavScreenProvider.AppHomeScreen()),
                                ScreenRegistry.get(NavScreenProvider.Chat.Conversation(chatId)))
                        }

                        else -> emptyList()
                    }
                }
                else -> emptyList()
            }
        } ?: emptyList()
    }
}