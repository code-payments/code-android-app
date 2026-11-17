package com.flipcash.app.internal.ui

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.cardexpand.CardExpansionController
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.core.navigation.TabBarVisibilityController
import com.flipcash.app.core.navigation.isTabHome
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.EmptyCodeNavigator
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether the bar is drawn, per shell, and whether it offers search. Under Group Money, Scanner and
 * You have no tab, so they are drawn without the bar however they were reached, and the bar has no
 * search button; under Classic every tab keeps the bar and its search button, as on code/cash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppNavigationBarShellTest {

    private val tabs = listOf(AppRoute.Tabs.Scanner, AppRoute.Tabs.Wallet, AppRoute.Tabs.Chats, AppRoute.Tabs.Menu)

    private fun visibility(
        shell: NavBarShell,
        vararg stack: NavKey,
        bottomBarMessages: List<Any> = emptyList(),
        billUp: Boolean = false,
        tabBarVisibility: TabBarVisibilityController = TabBarVisibilityController(),
        cardExpansion: CardExpansionController? = null,
    ) = AppNavigationBarVisibility(
        navigator = CodeNavigator(
            backStack = NavBackStack(*stack),
            resultStore = EmptyCodeNavigator.resultStore,
            onRootReached = {},
        ),
        bottomBarMessages = mutableStateOf(bottomBarMessages),
        billUp = mutableStateOf(billUp),
        tabBarVisibility = tabBarVisibility,
        cardExpansion = cardExpansion,
        shell = mutableStateOf(shell),
    )

    private fun barShown(shell: NavBarShell, vararg stack: NavKey): Boolean =
        visibility(shell, *stack).shown

    @Test
    fun `Classic draws the bar over all four tabs, Scanner and You included`() {
        tabs.forEach { tab -> assertTrue(barShown(NavBarShell.Classic, tab), "$tab") }
        assertTrue(barShown(NavBarShell.Classic, AppRoute.Tabs.Wallet, AppRoute.Tabs.Menu))
    }

    @Test
    fun `Group Money draws the bar over Groups and Money only`() {
        assertTrue(barShown(NavBarShell.GroupMoney, AppRoute.Tabs.Chats))
        assertTrue(barShown(NavBarShell.GroupMoney, AppRoute.Tabs.Wallet))
        assertFalse(barShown(NavBarShell.GroupMoney, AppRoute.Tabs.Scanner))
        assertFalse(barShown(NavBarShell.GroupMoney, AppRoute.Tabs.Menu))
    }

    @Test
    fun `You pushed from the Money avatar is drawn without the bar`() {
        assertFalse(barShown(NavBarShell.GroupMoney, AppRoute.Tabs.Wallet, AppRoute.Tabs.Menu))
    }

    @Test
    fun `only the Classic bar offers search`() {
        assertTrue(NavBarShell.Classic.offersSearch)
        assertFalse(NavBarShell.GroupMoney.offersSearch)
    }

    @Test
    fun `Search keeps the bar in both shells`() {
        assertTrue(barShown(NavBarShell.Classic, AppRoute.Tabs.Chats, AppRoute.Messaging.Search))
        assertTrue(barShown(NavBarShell.GroupMoney, AppRoute.Tabs.Chats, AppRoute.Messaging.Search))
    }

    @Test
    fun `Classic's tab homes are exactly the tab routes, so the bar inset is unchanged`() {
        val routes = tabs + listOf(AppRoute.Loading, AppRoute.Messaging.Search, null)
        routes.forEach { route ->
            assertEquals(route is AppRoute.Tabs, NavBarShell.Classic.isTabHome(route), "$route")
        }
        assertEquals(
            listOf<AppRoute>(AppRoute.Tabs.Wallet, AppRoute.Tabs.Chats),
            tabs.filter { NavBarShell.GroupMoney.isTabHome(it) },
        )
    }

    @Test
    fun `the soft bottom edge shows over the Group Money tab homes only`() {
        assertTrue(visibility(NavBarShell.GroupMoney, AppRoute.Tabs.Chats).showsBottomEdge)
        assertTrue(visibility(NavBarShell.GroupMoney, AppRoute.Tabs.Wallet).showsBottomEdge)
        assertFalse(visibility(NavBarShell.GroupMoney, AppRoute.Tabs.Scanner).showsBottomEdge)
        assertFalse(visibility(NavBarShell.GroupMoney, AppRoute.Tabs.Menu).showsBottomEdge)
        assertFalse(visibility(NavBarShell.GroupMoney, AppRoute.Tabs.Wallet, AppRoute.Tabs.Menu).showsBottomEdge)
        assertFalse(visibility(NavBarShell.GroupMoney, AppRoute.Tabs.Chats, AppRoute.Messaging.ArchivedChats).showsBottomEdge)
    }

    @Test
    fun `Classic has no soft bottom edge`() {
        tabs.forEach { tab -> assertFalse(visibility(NavBarShell.Classic, tab).showsBottomEdge, "$tab") }
    }

    @Test
    fun `Search draws its own bottom edge, so the bar's is left out`() {
        assertFalse(
            visibility(NavBarShell.GroupMoney, AppRoute.Tabs.Chats, AppRoute.Messaging.Search).showsBottomEdge,
        )
    }

    @Test
    fun `the soft bottom edge leaves with the bar`() {
        assertFalse(
            visibility(NavBarShell.GroupMoney, AppRoute.Tabs.Chats, billUp = true).showsBottomEdge,
        )
        assertFalse(
            visibility(NavBarShell.GroupMoney, AppRoute.Tabs.Chats, bottomBarMessages = listOf(Any()))
                .showsBottomEdge,
        )
        val hidden = TabBarVisibilityController().apply { hide() }
        assertFalse(
            visibility(NavBarShell.GroupMoney, AppRoute.Tabs.Chats, tabBarVisibility = hidden).showsBottomEdge,
        )
    }

    @Test
    fun `the soft bottom edge leaves once a wallet card has expanded over the deck`() {
        val expansion = CardExpansionController()
        runBlocking { expansion.snapTo(1f) }
        assertFalse(
            visibility(NavBarShell.GroupMoney, AppRoute.Tabs.Wallet, cardExpansion = expansion).showsBottomEdge,
        )
    }
}
