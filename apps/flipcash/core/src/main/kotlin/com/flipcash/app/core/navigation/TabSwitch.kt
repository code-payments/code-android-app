package com.flipcash.app.core.navigation

import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.AppRoute
import com.getcode.navigation.core.CodeNavigator

/**
 * Switch to [tab]'s home, keeping the tab homes already visited.
 *
 * A tab press used to clear the whole backstack, so every tab was rebuilt from nothing on every
 * press: Nav3 gives each entry its own `ViewModelStore` and saveable state, and both die with the
 * entry. Returning to the wallet meant a cold ViewModel behind its loading spinner, which is what
 * made the scanner-to-wallet switch after a claim read as a reload rather than a tab change.
 *
 * So the tab homes stay on the stack and the target moves to the top. Its entry — and with it its
 * ViewModels, `rememberSaveable` state and list scroll position — is the one already there, so the
 * tab comes back as the user left it.
 *
 * Back now walks the visited tabs before it leaves the app, rather than leaving from whichever tab
 * is showing. That is the cost of the retention: an entry has to be on the stack to survive, and a
 * stack the user can see is a stack they can go back through.
 *
 * Deeplinks are unaffected — they still arrive through `navigateAll`, which clears. A link is an
 * entry point into the app rather than a move between tabs, and it should not inherit whatever the
 * previous session left behind it.
 */
fun CodeNavigator.switchTab(tab: NavBarButton) {
    val current = backStack.toList()
    val next = current.afterSwitchingTo(tab)
    if (next == current) return

    Snapshot.withMutableSnapshot {
        backStack.clear()
        backStack.addAll(next)
    }
}

/**
 * The stack [switchTab] leaves behind, in the order back will walk it.
 *
 * Everything that is not a tab home is dropped: the screens pushed on the outgoing tab, and any
 * sheet over them. Clearing the stack always discarded those, and carrying them across would leave
 * another tab's detail screen sitting under the one being opened.
 *
 * A pure function over the keys, because that is the part worth asserting on — [switchTab] applies
 * it to a live [androidx.navigation3.runtime.NavBackStack] inside a snapshot, so the whole swap
 * lands in one frame and no entry is seen to leave and come back.
 */
fun List<NavKey>.afterSwitchingTo(tab: NavBarButton): List<NavKey> {
    val destination = tab.destinationRoute()
    val retained = filter { key ->
        val home = (key as? AppRoute)?.asNavBarTab()
        home != null && home != tab
    }
    return retained + destination
}
