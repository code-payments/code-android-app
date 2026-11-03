package com.flipcash.desktop.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flipcash.app.core.AppRoute
import com.flipcash.shared.chat.ui.ConversationReference
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

    /** The user hid the chat list (toggle, View → Hide Sidebar). Sticky, and saved with the window. */
    var sidebarHidden: Boolean by mutableStateOf(false)

    /** The full list floating over the chat while the pointer rests on the rail. */
    var sidebarPeek: Boolean by mutableStateOf(false)

    fun sidebarModeAt(widthClass: WidthClass): SidebarMode =
        sidebarModeFor(widthClass, contextOpen = context != null, hidden = sidebarHidden)

    fun contextPaneModeAt(widthClass: WidthClass): ContextPaneMode =
        contextPaneModeFor(widthClass, sidebarModeAt(widthClass))

    fun toggleSidebar() {
        sidebarHidden = !sidebarHidden
        sidebarPeek = false
    }

    /** The chat open in the main pane, or null before the user picks one. */
    var openChat: ConversationReference? by mutableStateOf(null)
        private set

    /** A chat picked from the list: compact shows it, and a floating list folds back to the rail. */
    fun openChat(chat: ConversationReference? = null) {
        if (chat != null) openChat = chat
        compactShowsChat = true
        sidebarPeek = false
    }

    /** Whether [chat] is the one open in the main pane, which is what the sidebar marks selected. */
    fun isOpen(chat: ConversationReference): Boolean = openChat?.chatId == chat.chatId

    /** What the sidebar's search field holds. Here rather than in the field so the peek shares it. */
    var searchQuery: String by mutableStateOf("")

    /** Set by the Search command (⌘K); the sidebar's search field takes focus and clears it. */
    var searchFocusRequested: Boolean by mutableStateOf(false)

    /** Brings the search field up: out of hiding, out of a chat at compact, or floated over the rail. */
    fun requestSearchFocus() {
        sidebarHidden = false
        compactShowsChat = false
        sidebarPeek = true
        searchFocusRequested = true
    }

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

    /**
     * Esc folds a floating list back to the rail, else closes the context pane when it floats over the
     * main pane; a column stays put.
     */
    fun onEscape(mode: ContextPaneMode): Boolean {
        if (sidebarPeek) {
            sidebarPeek = false
            return true
        }
        if (mode != ContextPaneMode.Overlay || context == null) return false
        context = null
        return true
    }
}
