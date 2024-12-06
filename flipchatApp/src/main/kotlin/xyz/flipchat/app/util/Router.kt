package xyz.flipchat.app.util

import androidx.core.net.toUri
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.model.ID
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.screens.ChildNavTab
import com.getcode.vendor.Base58
import dev.theolm.rinku.DeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import xyz.flipchat.app.features.home.tabs.CashTab
import xyz.flipchat.app.features.home.tabs.ChatTab
import xyz.flipchat.app.features.home.tabs.SettingsTab
import xyz.flipchat.services.user.UserManager

interface Router {
    fun checkTabs()
    val rootTabs: List<ChildNavTab>
    fun getInitialTabIndex(deeplink: DeepLink?): Int
    fun processDestination(deeplink: DeepLink?): List<Screen>
    fun processType(deeplink: DeepLink?): DeeplinkType?
    fun tabForIndex(index: Int): FcTab
}

enum class FcTab {
    Chat, Cash, Settings
}

sealed interface DeeplinkType {
    data class Login(val entropy: String) : DeeplinkType
    data class OpenRoom(val roomId: ID) : DeeplinkType
}

class RouterImpl(
    private val userManager: UserManager,
    private val tabIndexResolver: (FcTab) -> Int,
    private val indexTabResolver: (Int) -> FcTab,
) : Router, CoroutineScope by CoroutineScope(Dispatchers.IO) {
    companion object {
        val chats = listOf("chats")
        val cash = listOf("cash")
        val settings = listOf("settings")

        val login = listOf("login")
        val room = listOf("room")
    }

    override fun tabForIndex(index: Int) = indexTabResolver(index)

    private val commonTabs = listOf(ChatTab, CashTab)
    private val tabs = MutableStateFlow(commonTabs)

    override fun checkTabs() {
        launch {
            tabs.value = userManager.state
                .map { it.flags }
                .map {
                    if (it?.isStaff == true) {
                        commonTabs + SettingsTab
                    } else {
                        commonTabs
                    }
                }.firstOrNull() ?: commonTabs
        }
    }

    override val rootTabs: List<ChildNavTab>
        get() = tabs.value

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
            val type = processType(deeplink) ?: return emptyList()
            when (type) {
                is DeeplinkType.Login -> listOf(ScreenRegistry.get(NavScreenProvider.AppHomeScreen(deeplink)))
                is DeeplinkType.OpenRoom ->  listOf(
                    ScreenRegistry.get(NavScreenProvider.AppHomeScreen()),
                    ScreenRegistry.get(NavScreenProvider.Chat.Conversation(type.roomId))
                )
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

                        room.contains(deeplink.pathSegments[0]) -> {
                            val id = runCatching {
                                deeplink.data.toUri().getQueryParameter("r")
                            }.getOrNull() ?: return null
                            val roomId =  Base58.decode(id).toList()
                            DeeplinkType.OpenRoom(roomId = roomId)
                        }

                        else -> null
                    }
                }

                else -> null
            }
        }
    }
}