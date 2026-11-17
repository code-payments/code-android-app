package com.flipcash.app.currencycreator.internal

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.core.tokens.CurrencyCreatorResult
import com.getcode.navigation.Sheet
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.EmptyCodeNavigator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Leaving the currency creator unwinds to where it was opened. Each entry stack is built with the
 * same navigator calls production makes, so what [exitCurrencyCreator] sees is what the app holds:
 * - wallet tile, classic shell only (Money hides the tiles): the nav bar's tab switch
 *   (`AppNavigationBar.kt`, `replaceAll`), then the tile's push (`WalletScreen.kt`);
 * - Settings row, Group Money shell only: the Money tab, the avatar's push of You
 *   (`MoneyTitleBar.kt` via `WalletScreen.kt`), You's push of Settings (`MenuScreen.kt`), then the
 *   row's push (`SettingsScreen.kt`).
 * Neither stack holds a [Sheet].
 */
class CurrencyCreatorExitTest {

    private val route = AppRoute.Token.CurrencyCreator

    private val bill: Scannable.Payable = Scannable.Payable.forToken(
        token = MintMetadata(
            address = Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"),
            decimals = 6,
            name = "Test",
            symbol = "TST",
            createdAt = null,
            description = "",
            imageUrl = "",
            vmMetadata = VmMetadata(
                vm = PublicKey.fromBase58("11111111111111111111111111111111"),
                authority = PublicKey.fromBase58("11111111111111111111111111111111"),
                lockDurationInDays = 21,
            ),
            launchpadMetadata = null,
            billCustomizations = null,
            socialLinks = emptyList(),
            holderMetrics = HolderMetrics.None,
        ),
        amount = LocalFiat(
            underlyingTokenAmount = Fiat(5.0),
            nativeAmount = Fiat(5.0),
            rate = Rate.oneToOne,
            mint = Mint.usdf,
        ),
        didReceive = true,
    )

    private val shownBills = mutableListOf<Scannable.Payable>()

    private fun rootNavigator(onRootReached: () -> Unit = {}) = CodeNavigator(
        // Both shells launch on Groups/Chats (HomeRoute.kt `launchRoute`).
        backStack = NavBackStack<NavKey>(AppRoute.Tabs.Chats),
        resultStore = EmptyCodeNavigator.resultStore,
        onRootReached = onRootReached,
    )

    /** Wallet tile: Wallet tab, then the creator. */
    private fun walletTileStack(): CodeNavigator = rootNavigator().apply {
        replaceAll(AppRoute.Tabs.Wallet)
    }

    /** Settings row under Group Money: Money tab, You, Settings, then the creator. */
    private fun settingsRowStack(): CodeNavigator = rootNavigator().apply {
        replaceAll(AppRoute.Tabs.Wallet)
        push(AppRoute.Tabs.Menu)
        push(AppRoute.Menu.Settings)
    }

    private fun CodeNavigator.exit(
        shell: NavBarShell,
        result: CurrencyCreatorResult,
        purchaseAmount: Fiat,
    ) = exitCurrencyCreator(
        route = route,
        result = result,
        shell = shell,
        launchBill = bill,
        purchaseAmount = purchaseAmount,
        requestNotifications = {},
        showBill = { shownBills += it },
    )

    /** Pushes the creator, exits it once, and returns the stack it was opened from. */
    private fun CodeNavigator.openCreatorAndExit(
        shell: NavBarShell,
        result: CurrencyCreatorResult,
        purchaseAmount: Fiat,
    ): List<NavKey> {
        val opener = backStack.toList()
        push(route)
        assertFalse(backStack.any { it is Sheet }, "entry stack holds no sheet: $backStack")

        exit(shell, result, purchaseAmount)
        return opener
    }

    /**
     * Pins classic as it is, not as it should be: with no sheet on the stack, `hide()` pops
     * nothing, so the creator stays under its bill. That dead end has been on code/cash since
     * a12cf891b moved the wallet from a sheet to a tab. When classic is fixed this should read
     * `assertEquals(opener, navigator.backStack.toList())`.
     */
    @Test
    fun `funded launch from the wallet tile leaves classic on the creator with the bill up`() {
        val navigator = walletTileStack()

        val opener = navigator.openCreatorAndExit(
            NavBarShell.Classic,
            CurrencyCreatorResult.Success(),
            Fiat(5.0),
        )

        assertEquals(opener + route, navigator.backStack.toList())
        assertEquals(listOf(bill), shownBills)
    }

    @Test
    fun `funded launch from the Settings row ends on Settings with the bill up`() {
        val navigator = settingsRowStack()

        val opener = navigator.openCreatorAndExit(
            NavBarShell.GroupMoney,
            CurrencyCreatorResult.Success(),
            Fiat(5.0),
        )

        assertEquals(opener, navigator.backStack.toList())
        assertEquals(listOf(bill), shownBills)
    }

    @Test
    fun `funded launch completed on the funding step from Settings ends on Settings`() {
        val navigator = settingsRowStack()

        val opener = navigator.openCreatorAndExit(
            NavBarShell.GroupMoney,
            CurrencyCreatorResult.Success(completedInPlace = true),
            Fiat(5.0),
        )

        assertEquals(opener, navigator.backStack.toList())
        assertEquals(listOf(bill), shownBills)
    }

    /** The Processing step's success button stays tappable while the popped creator animates out. */
    @Test
    fun `a second funded exit after the pop leaves Settings and shows no second bill`() {
        val navigator = settingsRowStack()

        val opener = navigator.openCreatorAndExit(
            NavBarShell.GroupMoney,
            CurrencyCreatorResult.Success(),
            Fiat(5.0),
        )
        navigator.exit(NavBarShell.GroupMoney, CurrencyCreatorResult.Success(), Fiat(5.0))

        assertEquals(opener, navigator.backStack.toList())
        assertEquals(listOf(bill), shownBills)
    }

    @Test
    fun `unfunded launch from the Settings row closes back to Settings without a bill`() {
        val navigator = settingsRowStack()

        val opener = navigator.openCreatorAndExit(
            NavBarShell.GroupMoney,
            CurrencyCreatorResult.Success(),
            Fiat.Zero,
        )

        assertEquals(opener, navigator.backStack.toList())
        assertTrue(shownBills.isEmpty())
    }

    @Test
    fun `cancel from the Settings row closes back to Settings`() {
        val navigator = settingsRowStack()

        val opener = navigator.openCreatorAndExit(
            NavBarShell.GroupMoney,
            CurrencyCreatorResult.Canceled,
            Fiat(5.0),
        )

        assertEquals(opener, navigator.backStack.toList())
        assertTrue(shownBills.isEmpty())
    }

    /**
     * What `hide()` was written for: a creator opened inside a sheet runs on the sheet's own
     * navigator, seeded with the creator as its root (`AppScreenContent.kt` `SheetContent`), whose
     * root-reached callback dismisses the sheet. Both shells end there.
     */
    @Test
    fun `funded launch inside a sheet dismisses the sheet with the bill up`() {
        NavBarShell.entries.forEach { shell ->
            shownBills.clear()
            var sheetDismissed = false
            val sheetNavigator = CodeNavigator(
                backStack = NavBackStack<NavKey>(route),
                resultStore = EmptyCodeNavigator.resultStore,
                onRootReached = { sheetDismissed = true },
            )

            sheetNavigator.exit(shell, CurrencyCreatorResult.Success(), Fiat(5.0))

            assertTrue(sheetDismissed, "$shell dismisses the sheet")
            assertEquals(listOf(bill), shownBills, "$shell shows the bill")
        }
    }
}
