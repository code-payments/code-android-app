package com.flipcash.desktop

import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.balance.internal.WalletViewModel
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.getcode.opencode.exchange.Exchange
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.network.NetworkConnectivityListener
import com.flipcash.desktop.inject.FlipcashDesktopChannelModule
import com.flipcash.services.inject.FlipcashModule
import com.flipcash.desktop.inject.OpenCodeDesktopChannelModule
import com.getcode.opencode.inject.OpenCodeModule
import com.getcode.opencode.inject.SessionListenerModule
import com.getcode.opencode.inject.TransactorModule
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import javax.inject.Singleton

@Singleton
@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [
        OpenCodeModule::class,
        OpenCodeDesktopChannelModule::class,
        SessionListenerModule::class,
        TransactorModule::class,
        FlipcashModule::class,
        com.flipcash.services.inject.ChatCryptoModule::class,
        FlipcashDesktopChannelModule::class,
        DesktopModule::class,
    ],
)
interface DesktopGraph {
    val walletViewModel: WalletViewModel
    val selectTokenViewModel: SelectTokenViewModel
    val exchange: Exchange
    val resources: ResourceHelper
    val networkObserver: NetworkConnectivityListener
    val analytics: FlipcashAnalytics
    val userManager: com.flipcash.services.user.UserManager
    val accountController: com.flipcash.services.controllers.AccountController

    // Android constructs this eagerly in FlipcashApp.onCreate; it is what turns Events.OnLoggedIn into
    // SessionListener.onUserLoggedIn calls.
    val eventRepository: com.getcode.opencode.repositories.EventRepository

    // For DesktopWiringTest: the listener set, and singletons to check are shared across injection sites.
    val sessionListeners: Set<com.getcode.opencode.providers.SessionListener>
    val tokenCoordinator: com.flipcash.app.tokens.TokenCoordinator
    val eventBus: com.hoc081098.channeleventbus.ChannelEventBus
}
