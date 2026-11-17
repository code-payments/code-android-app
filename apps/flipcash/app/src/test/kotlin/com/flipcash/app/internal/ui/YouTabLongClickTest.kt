package com.flipcash.app.internal.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.AppRoute
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.EmptyCodeNavigator
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The You tab's long-press opens the account switcher only behind the Switch Accounts gate. The
 * tab bar attaches no long-press at all when this is null, so null is "nothing happens". The group
 * money shell has no You tab; the Money tab's avatar takes the same long-press.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class YouTabLongClickTest {

    private fun navigatorOn(tab: AppRoute.Tabs) = CodeNavigator(
        backStack = NavBackStack<NavKey>(tab),
        resultStore = EmptyCodeNavigator.resultStore,
        onRootReached = {},
    )

    @Test
    fun `gate closed gives the tab no long-press`() {
        val navigator = navigatorOn(AppRoute.Tabs.Menu)

        assertNull(youTabLongClick(canSwitchAccounts = false, navigator = navigator))
        assertEquals(listOf<NavKey>(AppRoute.Tabs.Menu), navigator.backStack.toList())
    }

    @Test
    fun `gate open presents the account switcher as a sheet`() {
        val navigator = navigatorOn(AppRoute.Tabs.Menu)

        val onLongClick = assertNotNull(youTabLongClick(canSwitchAccounts = true, navigator = navigator))
        onLongClick()

        assertEquals(
            listOf(AppRoute.Tabs.Menu, AppRoute.Main.Sheet(AppRoute.Menu.AccountSelection)),
            navigator.backStack.toList(),
        )
    }

    @Test
    fun `gate closed gives the Money avatar no long-press`() {
        val navigator = navigatorOn(AppRoute.Tabs.Wallet)

        assertNull(youTabLongClick(canSwitchAccounts = false, navigator = navigator))
        assertEquals(listOf<NavKey>(AppRoute.Tabs.Wallet), navigator.backStack.toList())
    }

    @Test
    fun `gate open presents the switcher over Money, and closing it returns to Money`() {
        val navigator = navigatorOn(AppRoute.Tabs.Wallet)

        val onLongClick = assertNotNull(youTabLongClick(canSwitchAccounts = true, navigator = navigator))
        onLongClick()

        assertEquals(
            listOf(AppRoute.Tabs.Wallet, AppRoute.Main.Sheet(AppRoute.Menu.AccountSelection)),
            navigator.backStack.toList(),
        )

        navigator.navigateBack()

        assertEquals(listOf<NavKey>(AppRoute.Tabs.Wallet), navigator.backStack.toList())
    }
}
