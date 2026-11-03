package com.flipcash.app.tipping.internal

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

/** Hilt entry point for the shared [ChatsViewModel]. */
@HiltViewModel
internal class HiltChatsViewModel @Inject constructor(
    exchange: Exchange,
    chatCoordinator: ChatCoordinator,
    userManager: UserManager,
    tokenCoordinator: TokenCoordinator,
    resources: ResourceHelper,
    dispatchers: DispatcherProvider,
    archiveStore: ChatArchiveStore,
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
    lobbyRequests = lobbyRequests,
    hostLobbies = hostLobbies,
    paymentRequests = paymentRequests,
)
