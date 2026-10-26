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
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.theme.FlipcashTheme
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.desktop.shell.ChatPane
import com.flipcash.desktop.shell.DesktopShell
import com.flipcash.desktop.shell.DesktopStartTab
import com.flipcash.desktop.shell.MoneyPane
import com.flipcash.desktop.shell.YouPane
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.opencode.compose.LocalExchange
import com.getcode.theme.CodeTheme
import com.getcode.util.resources.LocalResources
import com.getcode.utils.network.LocalNetworkObserver

/**
 * The signed-in window, with the locals and window background that Android's app root provides
 * (`App.kt` paints [CodeTheme.colors.background] behind every screen). The graph doesn't scope view
 * models, so each read of [DesktopGraph.walletViewModel] builds a new one; they're held here.
 */
@Composable
fun DesktopMainContent(graph: DesktopGraph, initialTab: NavBarButton = DesktopStartTab) {
    val walletViewModel = remember { graph.walletViewModel }
    val tokenViewModel = remember { graph.selectTokenViewModel }
    LaunchedEffect(tokenViewModel) {
        tokenViewModel.dispatchEvent(SelectTokenViewModel.Event.OnPurposeChanged(TokenPurpose.Balance))
    }
    val user by graph.userManager.state.collectAsState()
    val displayName = user.userProfile?.displayName.orEmpty()
    FlipcashTheme {
        CompositionLocalProvider(
            LocalExchange provides graph.exchange,
            LocalResources provides graph.resources,
            LocalNetworkObserver provides graph.networkObserver,
            LocalAnalytics provides graph.analytics,
        ) {
            Box(Modifier.fillMaxSize().background(CodeTheme.colors.background)) {
                DesktopShell(
                    chat = { ChatPane(it) },
                    money = { MoneyPane(it, walletViewModel, tokenViewModel) },
                    you = { YouPane() },
                    // Initials on the avatar gradient. The photo needs the blob URL resolver the
                    // Android app provides, which desktop doesn't have yet.
                    avatar = { modifier -> ContactAvatar(photoUri = null, displayName = displayName, modifier = modifier) },
                    initialTab = initialTab,
                )
            }
        }
    }
}
