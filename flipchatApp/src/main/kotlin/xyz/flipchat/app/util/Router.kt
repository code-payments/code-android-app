package xyz.flipchat.app.util

import androidx.core.net.toUri
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.model.ID
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.RoomInfoArgs
import com.getcode.navigation.screens.ChildNavTab
import com.getcode.util.resources.ResourceHelper
import com.getcode.vendor.Base58
import dev.theolm.rinku.DeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import xyz.flipchat.app.beta.Lab
import xyz.flipchat.app.beta.Labs
import xyz.flipchat.app.features.home.tabs.CashTab
import xyz.flipchat.app.features.home.tabs.ChatTab
import xyz.flipchat.app.features.home.tabs.ProfileTab
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.internal.db.FcAppDatabase
import xyz.flipchat.services.extensions.titleOrFallback
import xyz.flipchat.services.user.UserManager

interface Router {
    fun checkTabs()
    val rootTabs: List<ChildNavTab>
    fun getInitialTabIndex(deeplink: DeepLink?): Int
    suspend fun processDestination(deeplink: DeepLink?): List<Screen>
    fun processType(deeplink: DeepLink?): DeeplinkType?
    fun tabForIndex(index: Int): FcTab
}

enum class FcTab {
    Chat, Cash, Settings, Profile
}

sealed interface DeeplinkType {
    data class Login(val entropy: String) : DeeplinkType
    data class OpenRoomByNumber(val number: Long, val messageId: ID? = null) : DeeplinkType
    data class OpenRoomById(val id: ID, val messageId: ID? = null) : DeeplinkType
}

class RouterImpl(
    private val labs: Labs,
    private val userManager: UserManager,
    private val chatsController: ChatsController,
    private val resources: ResourceHelper,
    private val tabIndexResolver: (FcTab) -> Int,
    private val indexTabResolver: (Int) -> FcTab,
) : Router, CoroutineScope by CoroutineScope(Dispatchers.IO) {
    companion object {
        val chats = listOf("chats")
        val cash = listOf("cash")
        val settings = listOf("settings")
        val profile = listOf("profile")

        val login = listOf("login")
        val room = listOf("room", "id", "number")
    }

    private val db: FcAppDatabase
        get() = FcAppDatabase.requireInstance()

    override fun tabForIndex(index: Int) = indexTabResolver(index)

    private val commonTabs: List<ChildNavTab> =
        listOf(ChatTab(0), CashTab(1), ProfileTab(2))

    private val tabs = MutableStateFlow(commonTabs)

    override fun checkTabs() {
        // no-op right now
    }

    override val rootTabs: List<ChildNavTab>
        get() = tabs.value

    override fun getInitialTabIndex(deeplink: DeepLink?): Int {
        return deeplink?.let {
            when {
                deeplink.pathSegments.isEmpty() -> tabIndexResolver(FcTab.Chat)
                chats.contains(deeplink.pathSegments[0]) -> tabIndexResolver(FcTab.Chat)
                room.contains(deeplink.pathSegments[0]) -> tabIndexResolver(FcTab.Chat)
                cash.contains(deeplink.pathSegments[0]) -> tabIndexResolver(FcTab.Cash)
                settings.contains(deeplink.pathSegments[0]) -> tabIndexResolver(FcTab.Settings)
                profile.contains(deeplink.pathSegments[0]) -> tabIndexResolver(FcTab.Profile)
                else -> 0
            }
        } ?: 0
    }

    override suspend fun processDestination(deeplink: DeepLink?): List<Screen> {
        return deeplink?.let {
            val type = processType(deeplink) ?: return emptyList()
            when (type) {
                is DeeplinkType.Login -> listOf(ScreenRegistry.get(NavScreenProvider.AppHomeScreen(deeplink)))

                is DeeplinkType.OpenRoomByNumber -> {
                    val conversation = db.conversationDao().findConversationRaw(type.number)
                    val screens = mutableListOf(ScreenRegistry.get(NavScreenProvider.AppHomeScreen()))
                    if (conversation != null) {
                        screens.add(ScreenRegistry.get(NavScreenProvider.Room.Messages(conversation.id)))
                    } else {
                        val lookup = chatsController.lookupRoom(type.number).getOrNull()
                        if (lookup != null) {
                            val (room, members) = lookup
                            val moderator = members.firstOrNull { it.isModerator }

                            val args = RoomInfoArgs(
                                roomId = room.id,
                                roomNumber = room.roomNumber,
                                roomTitle = room.titleOrFallback(resources,),
                                memberCount = members.count(),
                                ownerId = room.ownerId,
                                hostName = moderator?.identity?.displayName,
                                messagingFeeQuarks = room.messagingFee.quarks,
                            )

                            screens.add(
                                ScreenRegistry.get(
                                    NavScreenProvider.Room.Preview(args = args, returnToSender = true)
                                )
                            )
                        }
                    }

                    screens
                }

                is DeeplinkType.OpenRoomById -> {
                    val conversation = db.conversationDao().findConversationRaw(type.id)
                    val screens = mutableListOf(ScreenRegistry.get(NavScreenProvider.AppHomeScreen()))
                    if (conversation != null) {
                        screens.add(ScreenRegistry.get(NavScreenProvider.Room.Messages(conversation.id)))
                    } else {
                        val lookup = chatsController.lookupRoom(type.id).getOrNull()
                        if (lookup != null) {
                            val (room, members) = lookup
                            val moderator = members.firstOrNull { it.isModerator }

                            val args = RoomInfoArgs(
                                roomId = room.id,
                                roomNumber = room.roomNumber,
                                roomTitle = room.titleOrFallback(resources,),
                                memberCount = members.count(),
                                ownerId = room.ownerId,
                                hostName = moderator?.identity?.displayName,
                                messagingFeeQuarks = room.messagingFee.quarks,
                            )

                            screens.add(
                                ScreenRegistry.get(
                                    NavScreenProvider.Room.Preview(args = args, returnToSender = true)
                                )
                            )
                        }
                    }

                    screens
                }
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

                2 -> {
                    when {
                        room.contains(deeplink.pathSegments[0]) -> {
                            when (val specifier = deeplink.pathSegments[0]) {
                                "room",
                                "number" -> {
                                    val number = runCatching {
                                        deeplink.pathSegments[1].toLongOrNull()
                                    }.getOrNull() ?: return null

                                    val messageId = runCatching {
                                        deeplink.data.toUri().getQueryParameter("m")?.let { Base58.decode(it).toList() }
                                    }.getOrNull()

                                    DeeplinkType.OpenRoomByNumber(number = number, messageId = messageId)
                                }
                                "id" -> {
                                    val id = runCatching {
                                        deeplink.pathSegments[1]
                                    }.getOrNull()?.let { Base58.decode(it).toList() } ?: return null

                                    val messageId = runCatching {
                                        deeplink.data.toUri().getQueryParameter("m")?.let { Base58.decode(it).toList() }
                                    }.getOrNull()

                                    DeeplinkType.OpenRoomById(id = id, messageId = messageId)
                                }
                                else -> return null
                            }
                        }

                        else -> null
                    }
                }

                else -> null
            }
        }
    }
}