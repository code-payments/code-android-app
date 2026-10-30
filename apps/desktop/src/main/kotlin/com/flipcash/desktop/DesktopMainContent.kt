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
import com.flipcash.desktop.shell.ChatListPlaceholder
import com.flipcash.desktop.shell.ContextContent
import com.flipcash.desktop.shell.ContextPane
import com.flipcash.desktop.shell.ConversationPlaceholder
import com.flipcash.desktop.shell.DesktopNavState
import com.flipcash.desktop.shell.DesktopShell
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
) {
    val nav = remember { DesktopNavState().apply { context = initialContext } }
    val walletViewModel = remember { graph.walletViewModel }
    val tokenViewModel = remember { graph.selectTokenViewModel }
    LaunchedEffect(tokenViewModel) {
        tokenViewModel.dispatchEvent(SelectTokenViewModel.Event.OnPurposeChanged(TokenPurpose.Balance))
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
                    floatingSidebar = isMac,
                    sidebar = { m -> ChatListPlaceholder(onOpenChat = { nav.compactShowsChat = true }, modifier = m) },
                    main = { m -> ConversationPlaceholder(m) },
                    context = { content, m ->
                        ContextPane(
                            content = content,
                            nav = nav,
                            wallet = { wm -> WalletPane(nav, walletViewModel, tokenViewModel, wm) },
                            modifier = m,
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
