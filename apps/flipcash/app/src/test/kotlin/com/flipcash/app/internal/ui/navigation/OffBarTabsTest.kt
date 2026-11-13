package com.flipcash.app.internal.ui.navigation

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where Scanner and You close back to under the Group Money shell, which draws them without the bar.
 * A scan link and a self-profile link replace the whole stack, so the tab the person came from has to
 * be remembered from the stacks seen before; the Money avatar pushes You, so its close pops.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OffBarTabsTest {

    private val navigator = CodeNavigator(
        backStack = NavBackStack<NavKey>(AppRoute.Loading),
        resultStore = EmptyCodeNavigator.resultStore,
        onRootReached = {},
    )
    private val offBarTabs = OffBarTabs(navigator).also { it.track(navigator.backStack.toList()) }

    /** Moves to [stack] and lets [offBarTabs] see it, as rememberOffBarTabs does on each change. */
    private fun show(vararg stack: NavKey) {
        navigator.replaceAll(stack.toList())
        offBarTabs.track(navigator.backStack.toList())
    }

    private fun push(route: NavKey) {
        navigator.push(route)
        offBarTabs.track(navigator.backStack.toList())
    }

    private fun stack(): List<NavKey> = navigator.backStack.toList()

    @Test
    fun `a scan link from Money closes back to Money`() {
        show(AppRoute.Tabs.Wallet)
        show(AppRoute.Tabs.Scanner)

        assertTrue(offBarTabs.takesSystemBack)
        offBarTabs.close()

        assertEquals(listOf<NavKey>(AppRoute.Tabs.Wallet), stack())
    }

    @Test
    fun `a scan link from Groups closes back to Groups`() {
        show(AppRoute.Tabs.Wallet)
        show(AppRoute.Tabs.Chats)
        show(AppRoute.Tabs.Scanner)

        offBarTabs.close()

        assertEquals(listOf<NavKey>(AppRoute.Tabs.Chats), stack())
    }

    @Test
    fun `You from the Money avatar pops back to Money, and leaves system back to the navigator`() {
        show(AppRoute.Tabs.Wallet)
        push(AppRoute.Tabs.Menu)

        assertFalse(offBarTabs.takesSystemBack)
        offBarTabs.close()

        assertEquals(listOf<NavKey>(AppRoute.Tabs.Wallet), stack())
    }

    @Test
    fun `a self-profile link from Money closes back to Money`() {
        show(AppRoute.Tabs.Wallet)
        show(AppRoute.Tabs.Menu)

        offBarTabs.close()

        assertEquals(listOf<NavKey>(AppRoute.Tabs.Wallet), stack())
    }

    @Test
    fun `Scanner and You do not move the tab they close back to`() {
        show(AppRoute.Tabs.Wallet)
        show(AppRoute.Tabs.Menu)
        show(AppRoute.Tabs.Scanner)

        offBarTabs.close()

        assertEquals(listOf<NavKey>(AppRoute.Tabs.Wallet), stack())
    }

    @Test
    fun `a link that lands on Scanner before any tab closes to Groups`() {
        show(AppRoute.Tabs.Scanner)

        offBarTabs.close()

        assertEquals(listOf<NavKey>(AppRoute.Tabs.Chats), stack())
    }

    @Test
    fun `leaving the tabs forgets the last one`() {
        show(AppRoute.Tabs.Wallet)
        show(AppRoute.Loading)
        show(AppRoute.Tabs.Menu)

        offBarTabs.close()

        assertEquals(listOf<NavKey>(AppRoute.Tabs.Chats), stack())
    }
}
