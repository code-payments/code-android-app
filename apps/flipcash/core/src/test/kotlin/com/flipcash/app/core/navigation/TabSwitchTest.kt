package com.flipcash.app.core.navigation

import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.AppRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * A tab press used to clear the backstack, which took each tab's ViewModels and saved state with it.
 * These assert the shape of the stack it leaves instead — the retained entries are the whole point,
 * since an entry that stays on the stack is an entry Nav3 does not tear down.
 */
class TabSwitchTest {

    private val scanner = AppRoute.Main.Scanner
    private val wallet = AppRoute.Sheets.Wallet
    private val menu = AppRoute.Sheets.Menu

    @Test
    fun `the tab already visited stays on the stack, under the one being opened`() {
        val stack = listOf<NavKey>(wallet)

        assertEquals(listOf(wallet, scanner), stack.afterSwitchingTo(NavBarButton.Scanner))
    }

    @Test
    fun `switching back reuses the entry that is already there`() {
        val stack = listOf<NavKey>(wallet).afterSwitchingTo(NavBarButton.Scanner)

        val back = stack.afterSwitchingTo(NavBarButton.Wallet)

        assertEquals(listOf(scanner, wallet), back)
        // Same key instance, so it keeps the same contentKey and Nav3 hands the entry — its
        // ViewModelStore, its saveable state — straight back rather than building a new one.
        assertSame(wallet, back.last())
    }

    @Test
    fun `a tab is never on the stack twice, however often it is pressed`() {
        var stack = listOf<NavKey>(wallet)
        repeat(3) {
            stack = stack.afterSwitchingTo(NavBarButton.Scanner).afterSwitchingTo(NavBarButton.Wallet)
        }

        assertEquals(listOf(scanner, wallet), stack)
    }

    @Test
    fun `screens pushed on the outgoing tab are dropped, not carried across`() {
        val stack = listOf<NavKey>(wallet, AppRoute.Main.Sheet(AppRoute.Sheets.ActivityHistory))

        // The wallet's home is kept — that is the entry being retained — but its open sheet is not,
        // or it would sit underneath the scanner and be what back returned to.
        assertEquals(listOf(wallet, scanner), stack.afterSwitchingTo(NavBarButton.Scanner))
    }

    @Test
    fun `pressing the tab you are already on pops back to its home`() {
        val stack = listOf<NavKey>(wallet, AppRoute.Main.Sheet(AppRoute.Sheets.ActivityHistory))

        assertEquals(listOf(wallet), stack.afterSwitchingTo(NavBarButton.Wallet))
    }

    @Test
    fun `pressing the tab you are on with nothing over it changes nothing`() {
        val stack = listOf<NavKey>(scanner, wallet)

        assertEquals(stack, stack.afterSwitchingTo(NavBarButton.Wallet))
    }

    @Test
    fun `back walks the tabs in the order they were visited`() {
        var stack = listOf<NavKey>(wallet)
        stack = stack.afterSwitchingTo(NavBarButton.TipCard)
        stack = stack.afterSwitchingTo(NavBarButton.Scanner)

        // Back from the scanner returns to the You tab, then the wallet, then leaves the app —
        // the deliberate cost of keeping the entries alive.
        assertEquals(listOf(wallet, menu, scanner), stack)
    }
}
