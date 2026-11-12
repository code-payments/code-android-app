package com.flipcash.app.internal.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.LocalNavBarShell
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.core.navigation.destinationRoute
import com.flipcash.app.core.navigation.isTabHome
import com.flipcash.app.core.navigation.selectedTab
import com.getcode.navigation.core.CodeNavigator

/**
 * Scanner and You under the Group Money shell, whose bar has no tab for either. A scan link, the
 * Money avatar or a link to your own profile still opens them; they are drawn without the bar and
 * close back to the bar tab the person came from, as on iOS (HomeTabView's `lastBarTab`).
 */
@Stable
internal class OffBarTabs(private val navigator: CodeNavigator) {

    /** The Group Money bar tab to close back to: the last one seen on the stack, Groups until then. */
    var lastBarTab: NavBarButton by mutableStateOf(NavBarButton.Chats)
        private set

    /**
     * Notes [backStack]'s Group Money bar tab, whatever shell is drawn, so turning the shell on from
     * You closes back to the tab the person was on. Leaving the tabs altogether (signing out)
     * forgets it.
     */
    fun track(backStack: List<NavKey>) {
        if (backStack.none { it is AppRoute.Tabs }) {
            lastBarTab = NavBarButton.Chats
            return
        }
        NavBarShell.GroupMoney.selectedTab(backStack)?.let { lastBarTab = it }
    }

    /**
     * Leaves Scanner or You. Pushed over a tab (the Money avatar), it pops back to it; as the whole
     * stack (a link replaced it), it swaps in [lastBarTab].
     */
    fun close() {
        if (navigator.backStack.size > 1) {
            navigator.navigateBack()
        } else {
            navigator.replaceAll(lastBarTab.destinationRoute())
        }
    }

    /**
     * Whether system back on a screen drawn without the bar is [close]'s to take: only with no screen
     * under it, where back would otherwise leave the app. Pushed over a tab, the navigator pops it.
     */
    val takesSystemBack: Boolean get() = navigator.backStack.size <= 1

    /**
     * The close for [route]'s screen when the shell draws it without the bar, or null when [route] is
     * one of the shell's tabs (every tab under Classic, as today).
     *
     * System back closes the same way (see [takesSystemBack]). Call it before composing the screen: a
     * back handler composed later (You's expanded card, the bill) takes back first.
     */
    @Composable
    fun closeFor(route: AppRoute.Tabs): (() -> Unit)? {
        if (LocalNavBarShell.current.isTabHome(route)) return null
        BackHandler(enabled = takesSystemBack) { close() }
        return ::close
    }
}

@Composable
internal fun rememberOffBarTabs(navigator: CodeNavigator): OffBarTabs {
    val offBarTabs = remember(navigator) { OffBarTabs(navigator) }
    LaunchedEffect(offBarTabs) {
        snapshotFlow { navigator.backStack.toList() }.collect(offBarTabs::track)
    }
    return offBarTabs
}
