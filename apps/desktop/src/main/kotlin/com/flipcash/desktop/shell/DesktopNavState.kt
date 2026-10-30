package com.flipcash.desktop.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flipcash.app.core.AppRoute
import com.getcode.solana.keys.Mint

/** What the main pane shows. The leaderboard replaces the chat until Back (spec: Leaderboard). */
sealed interface MainContent {
    data object Chat : MainContent
    data object Leaderboard : MainContent
}

/** What the context pane shows. It holds one thing at a time (context node 13041:1986). */
sealed interface ContextContent {
    data object Info : ContextContent
    data object Wallet : ContextContent
    data object You : ContextContent

    /** A currency, with what the pane showed before it so Back returns there. */
    data class Currency(val mint: Mint, val from: ContextContent?) : ContextContent

    /** A screen with no JVM build yet. */
    data class Unavailable(val name: String) : ContextContent
}

/**
 * What each pane of the window shows. Plain state so the rules are tested without Compose; the
 * fields are snapshot state so the shell recomposes when they change.
 */
class DesktopNavState {
    var main: MainContent by mutableStateOf(MainContent.Chat)
    var context: ContextContent? by mutableStateOf(null)

    /** Below 600 the window shows the list or the chat; true while it shows the chat. */
    var compactShowsChat: Boolean by mutableStateOf(false)

    fun toggleInfo() {
        context = if (context == ContextContent.Info) null else ContextContent.Info
    }

    fun openWallet() {
        context = ContextContent.Wallet
    }

    fun openYou() {
        context = ContextContent.You
    }

    fun openCurrency(mint: Mint) {
        context = ContextContent.Currency(mint, from = context)
    }

    /** A route a shared screen asks for. Desktop has no scanner, so that one is dropped. */
    fun openRoute(route: AppRoute) {
        when (route) {
            is AppRoute.Token.Info -> openCurrency(route.mint)
            is AppRoute.Tabs.Scanner -> Unit
            else -> context = ContextContent.Unavailable(route::class.simpleName ?: "Screen")
        }
    }

    /** One step back: a currency to where it came from, the leaderboard to the chat, the compact chat to the list. */
    fun back(): Boolean {
        val shown = context
        when {
            shown is ContextContent.Currency -> context = shown.from
            main != MainContent.Chat -> main = MainContent.Chat
            compactShowsChat -> compactShowsChat = false
            else -> return false
        }
        return true
    }

    fun closeContext() {
        context = null
    }

    /** Esc closes the context pane only when it slides over the main pane; a column stays put. */
    fun onEscape(mode: ContextPaneMode): Boolean {
        if (mode != ContextPaneMode.Overlay || context == null) return false
        context = null
        return true
    }
}
