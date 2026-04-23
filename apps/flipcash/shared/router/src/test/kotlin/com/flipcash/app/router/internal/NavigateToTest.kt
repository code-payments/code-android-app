package com.flipcash.app.router.internal

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.extensions.navigateTo
import com.flipcash.app.core.extensions.resolveRoutes
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.EmptyCodeNavigator
import com.getcode.navigation.core.NavOptions
import com.getcode.solana.keys.Mint
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NavigateToTest {

    private val quietOptions = NavOptions(debugRouting = false)

    private fun createNavigator(vararg entries: NavKey): CodeNavigator {
        val backStack = NavBackStack<NavKey>(entries.first())
        entries.drop(1).forEach { backStack.add(it) }
        return CodeNavigator(
            backStack = backStack,
            resultStore = EmptyCodeNavigator.resultStore,
            onRootReached = {},
        )
    }

    // region Direct navigation (no existing sheet)

    @Test
    fun `navigateTo without existing sheet navigates directly`() {
        val navigator = createNavigator(AppRoute.Main.Scanner)
        val mint = Mint("So11111111111111111111111111111111111111112")

        navigator.navigateTo(
            listOf(AppRoute.Sheets.Wallet, AppRoute.Token.Info(mint)),
            options = quietOptions,
        )

        assertNull(navigator.pendingSheetDismiss)
        assertEquals(2, navigator.backStack.size)
        assertIs<AppRoute.Main.Sheet>(navigator.backStack.last())
    }

    @Test
    fun `navigateTo non-sheet routes navigates directly even with existing sheet`() {
        val navigator = createNavigator(
            AppRoute.Main.Scanner,
            AppRoute.Main.Sheet(AppRoute.Sheets.Wallet),
        )

        navigator.navigateTo(listOf(AppRoute.Menu.MyAccount), options = quietOptions)

        assertNull(navigator.pendingSheetDismiss)
    }

    // endregion

    // region Dismiss-then-replace (existing sheet + new sheet)

    @Test
    fun `navigateTo with existing sheet sets pendingSheetDismiss`() {
        val navigator = createNavigator(
            AppRoute.Main.Scanner,
            AppRoute.Main.Sheet(AppRoute.Sheets.Wallet),
        )
        val mint = Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")

        navigator.navigateTo(
            listOf(AppRoute.Sheets.Wallet, AppRoute.Token.Info(mint)),
            options = quietOptions,
        )

        assertNotNull(navigator.pendingSheetDismiss)
        // Backstack unchanged until the callback fires
        assertEquals(2, navigator.backStack.size)
    }

    @Test
    fun `pendingSheetDismiss callback increments sheetGeneration`() {
        val navigator = createNavigator(
            AppRoute.Main.Scanner,
            AppRoute.Main.Sheet(AppRoute.Sheets.Wallet),
        )
        val initialGeneration = navigator.sheetGeneration

        navigator.navigateTo(listOf(AppRoute.Sheets.Wallet), options = quietOptions)

        // Simulate what ModalBottomSheetScene does: remove old sheet, then invoke callback
        navigator.backStack.removeAt(navigator.backStack.lastIndex)
        navigator.pendingSheetDismiss!!.invoke()

        assertEquals(initialGeneration + 1, navigator.sheetGeneration)
    }

    @Test
    fun `pendingSheetDismiss callback navigates to new routes`() {
        val navigator = createNavigator(
            AppRoute.Main.Scanner,
            AppRoute.Main.Sheet(AppRoute.Sheets.Wallet),
        )
        val mint = Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")

        navigator.navigateTo(
            listOf(AppRoute.Sheets.Wallet, AppRoute.Token.Info(mint)),
            options = quietOptions,
        )

        // Simulate dismiss: remove old sheet entry, then callback fires
        navigator.backStack.removeAt(navigator.backStack.lastIndex)
        navigator.pendingSheetDismiss!!.invoke()

        val last = navigator.backStack.last()
        assertIs<AppRoute.Main.Sheet>(last)
        assertEquals(AppRoute.Sheets.Wallet, last.initialRoute)
        assertIs<AppRoute.Token.Info>(last.innerRoutes.single())
    }

    @Test
    fun `repeated dismiss-then-replace increments generation each time`() {
        val navigator = createNavigator(
            AppRoute.Main.Scanner,
            AppRoute.Main.Sheet(AppRoute.Sheets.Wallet),
        )

        // First replace
        navigator.navigateTo(listOf(AppRoute.Sheets.Wallet), options = quietOptions)
        navigator.backStack.removeAt(navigator.backStack.lastIndex)
        navigator.pendingSheetDismiss!!.invoke()
        assertEquals(1, navigator.sheetGeneration)

        // Second replace
        navigator.navigateTo(listOf(AppRoute.Sheets.Wallet), options = quietOptions)
        navigator.backStack.removeAt(navigator.backStack.lastIndex)
        navigator.pendingSheetDismiss!!.invoke()
        assertEquals(2, navigator.sheetGeneration)
    }

    // endregion

    // region Edge cases

    @Test
    fun `empty routes is a no-op`() {
        val navigator = createNavigator(AppRoute.Main.Scanner)

        navigator.navigateTo(emptyList(), options = quietOptions)

        assertEquals(1, navigator.backStack.size)
        assertNull(navigator.pendingSheetDismiss)
    }

    @Test
    fun `same token dismiss-then-replace works`() {
        val mint = Mint("So11111111111111111111111111111111111111112")
        val navigator = createNavigator(
            AppRoute.Main.Scanner,
            AppRoute.Main.Sheet(
                AppRoute.Sheets.Wallet,
                listOf(AppRoute.Token.Info(mint, fromDeeplink = true)),
            ),
        )

        navigator.navigateTo(
            listOf(AppRoute.Sheets.Wallet, AppRoute.Token.Info(mint, fromDeeplink = true)),
            options = quietOptions,
        )

        // Should still go through dismiss-then-replace path
        assertNotNull(navigator.pendingSheetDismiss)
    }

    @Test
    fun `pendingSheetDismiss callback deduplicates when onBack removes wrong entry`() {
        // Reproduces the scenario where a navigation happens during the dismiss animation,
        // causing onBack() to remove the wrong entry and leaving a stale sheet on the
        // backstack. The callback's navigate() must not produce a duplicate Sheet key
        // (which would crash SaveableStateProvider).
        val navigator = createNavigator(
            AppRoute.Main.Scanner,
            AppRoute.Main.Sheet(AppRoute.Sheets.Wallet),
        )

        navigator.navigateTo(listOf(AppRoute.Sheets.Wallet), options = quietOptions)

        // Simulate: a route is pushed during the dismiss animation
        navigator.backStack.add(AppRoute.Menu.MyAccount)
        // onBack() removes the last entry (MyAccount), NOT the old sheet
        navigator.backStack.removeAt(navigator.backStack.lastIndex)
        // Old sheet is still on the backstack
        assertIs<AppRoute.Main.Sheet>(navigator.backStack.last())

        // Callback fires — must not produce a duplicate Sheet
        navigator.pendingSheetDismiss!!.invoke()

        val sheets = navigator.backStack.filterIsInstance<AppRoute.Main.Sheet>()
        assertEquals(1, sheets.size, "Expected exactly one Sheet on the backstack")
    }

    // endregion
}
