package com.flipcash.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.flipcash.app.analytics.LocalAnalytics
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.theme.FlipcashTheme
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.desktop.shell.ConversationPane
import com.flipcash.desktop.shell.ConversationToolbarTitle
import com.flipcash.desktop.shell.DesktopSidebar
import com.flipcash.desktop.shell.ContextContent
import com.flipcash.desktop.shell.ContextPane
import com.flipcash.desktop.shell.DesktopNavState
import com.flipcash.desktop.shell.DesktopShell
import com.flipcash.desktop.shell.RailFooter
import com.flipcash.desktop.shell.SidebarFooter
import com.flipcash.desktop.shell.WalletPane
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.opencode.compose.LocalExchange
import com.getcode.theme.CodeTheme
import com.getcode.util.resources.LocalResources
import com.getcode.utils.network.LocalNetworkObserver

/**
 * The signed-in window, with the locals and window background that Android's app root provides
 * (`App.kt` paints [CodeTheme.colors.background] behind every screen). The graph doesn't scope view
 * models, so each read of [DesktopGraph.walletViewModel] builds a new one; they're held here.
 * [initialContext] opens the context pane on first composition, for snapshots.
 */
@Composable
fun DesktopMainContent(
    graph: DesktopGraph,
    initialContext: ContextContent? = null,
    onSettings: () -> Unit = {},
    initialChatIndex: Int? = null,
    nav: DesktopNavState = remember { DesktopNavState().apply { context = initialContext } },
) {
    val walletViewModel = remember { graph.walletViewModel }
    val tokenViewModel = remember { graph.selectTokenViewModel }
    val chatsViewModel = remember { graph.chatsViewModel }
    LaunchedEffect(tokenViewModel) {
        tokenViewModel.dispatchEvent(SelectTokenViewModel.Event.OnPurposeChanged(TokenPurpose.Balance))
    }
    val chatsState by chatsViewModel.stateFlow.collectAsState()
    // Snapshots only: opens the chat at this index once the list loads, so a render shows a selection.
    if (initialChatIndex != null) {
        LaunchedEffect(chatsState.chats) {
            chatsState.visibleChats.getOrNull(initialChatIndex)?.let { if (nav.openChat == null) nav.openChat(it) }
        }
    }
    val user by graph.userManager.state.collectAsState()
    val displayName = user.userProfile?.displayName.orEmpty()
    val tokenState by tokenViewModel.stateFlow.collectAsState()
    // The same total the wallet header shows.
    val balance = tokenState.totalBalance?.nativeAmount?.formatted().orEmpty()
    FlipcashTheme {
        CompositionLocalProvider(
            LocalExchange provides graph.exchange,
            LocalResources provides graph.resources,
            LocalNetworkObserver provides graph.networkObserver,
            LocalAnalytics provides graph.analytics,
        ) {
            Box(Modifier.fillMaxSize().background(CodeTheme.colors.background)) {
                DesktopShell(
                    nav = nav,
                    sidebar = { m -> DesktopSidebar(chatsState, nav, chatsViewModel::selectFilter, m) },
                    main = { m -> ConversationPane(nav.openChat, m) },
                    toolbar = { ConversationToolbarTitle(nav.openChat) },
                    context = { content, m ->
                        ContextPane(
                            content = content,
                            nav = nav,
                            wallet = { wm -> WalletPane(nav, walletViewModel, tokenViewModel, wm) },
                            modifier = m,
                        )
                    },
                    railFooter = { m ->
                        RailFooter(
                            balance = balance,
                            onYou = nav::openYou,
                            onWallet = nav::openWallet,
                            onSettings = onSettings,
                            modifier = m,
                            avatar = { am -> ContactAvatar(photoUri = null, displayName = displayName, modifier = am) },
                        )
                    },
                    footer = { m ->
                        SidebarFooter(
                            displayName = displayName,
                            balance = balance,
                            onYou = nav::openYou,
                            onWallet = nav::openWallet,
                            onSettings = onSettings,
                            modifier = m,
                            // Initials on the avatar gradient. The photo needs the blob URL resolver the
                            // Android app provides, which desktop doesn't have yet.
                            avatar = { am -> ContactAvatar(photoUri = null, displayName = displayName, modifier = am) },
                        )
                    },
                )
            }
        }
    }
}
