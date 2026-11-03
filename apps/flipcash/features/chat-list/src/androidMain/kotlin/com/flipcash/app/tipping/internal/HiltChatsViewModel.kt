package com.flipcash.app.tipping.internal

import com.flipcash.core.resources.Res
import com.flipcash.core.resources.action_markAsRead
import com.flipcash.core.resources.prompt_description_readAllChats
import com.flipcash.core.resources.prompt_title_readAllChats
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatArchiveStore
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.HostLobbies
import com.flipcash.shared.chat.LobbyRequests
import com.flipcash.shared.chat.PaymentRequests
import com.getcode.opencode.exchange.Exchange
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Hilt entry point for the shared [ChatsViewModel], plus Read All's Android-only confirm prompt. */
@HiltViewModel
internal class HiltChatsViewModel @Inject constructor(
    exchange: Exchange,
    chatCoordinator: ChatCoordinator,
    userManager: UserManager,
    tokenCoordinator: TokenCoordinator,
    private val resources: ResourceHelper,
    dispatchers: DispatcherProvider,
    archiveStore: ChatArchiveStore,
    rowActions: ChatRowActions,
    lobbyRequests: LobbyRequests,
    hostLobbies: HostLobbies,
    paymentRequests: PaymentRequests,
) : ChatsViewModel(
    exchange = exchange,
    chatCoordinator = chatCoordinator,
    userManager = userManager,
    tokenCoordinator = tokenCoordinator,
    resources = resources,
    dispatchers = dispatchers,
    archiveStore = archiveStore,
    rowActions = rowActions,
    lobbyRequests = lobbyRequests,
    hostLobbies = hostLobbies,
    paymentRequests = paymentRequests,
) {

    /**
     * Read All asks first: it moves the READ pointer, which everyone in those chats sees. Cancel
     * leaves everything as it was; Mark as Read runs [readAll]. Here rather than in [ChatsViewModel]
     * because [BottomBarManager] is Android-only.
     */
    fun requestReadAll() {
        BottomBarManager.showMessage(
            title = resources.getString(Res.string.prompt_title_readAllChats),
            message = resources.getString(Res.string.prompt_description_readAllChats),
            actions = listOf(BottomBarAction(text = resources.getString(Res.string.action_markAsRead)) { readAll() }),
            showCancel = true,
        )
    }
}
