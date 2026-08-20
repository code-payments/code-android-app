package com.flipcash.app.router.internal

import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.extensions.resolveBackStack
import com.flipcash.app.core.extensions.resolveRoutes
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.services.models.chat.ChatId
import com.getcode.solana.keys.Mint
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ResolveRoutesTest {

    private val mint = Mint("So11111111111111111111111111111111111111112")

    // region Empty / non-sheet routes

    @Test
    fun `empty list returns empty`() {
        assertEquals(emptyList(), resolveRoutes(emptyList()))
    }

    @Test
    fun `non-sheet routes pass through unchanged`() {
        val routes = listOf(AppRoute.Main.Scanner, AppRoute.Menu.MyAccount)
        assertEquals(routes, resolveRoutes(routes))
    }

    // endregion

    // region Sheet wrapping — genuine modals (no tab home) bundle into Main.Sheet

    @Test
    fun `single sheet route is wrapped in Main Sheet`() {
        val resolved = resolveRoutes(listOf(AppRoute.Sheets.ActivityHistory))
        assertEquals(1, resolved.size)
        val sheet = resolved.single()
        assertIs<AppRoute.Main.Sheet>(sheet)
        assertEquals(AppRoute.Sheets.ActivityHistory, sheet.initialRoute)
        assertEquals(emptyList(), sheet.innerRoutes)
    }

    @Test
    fun `sheet with inner routes bundles into Main Sheet`() {
        val routes = listOf(
            AppRoute.Sheets.ActivityHistory,
            AppRoute.Token.Info(mint, fromDeeplink = true),
        )

        val resolved = resolveRoutes(routes)
        assertEquals(1, resolved.size)
        val sheet = resolved.single()
        assertIs<AppRoute.Main.Sheet>(sheet)
        assertEquals(AppRoute.Sheets.ActivityHistory, sheet.initialRoute)
        assertEquals(1, sheet.innerRoutes.size)
        assertIs<AppRoute.Token.Info>(sheet.innerRoutes[0])
    }

    @Test
    fun `sheet with multiple inner routes bundles all`() {
        val routes = listOf(
            AppRoute.Sheets.ActivityHistory,
            AppRoute.Menu.MyAccount,
            AppRoute.Verification(
                origin = AppRoute.Menu.MyAccount,
                includePhone = false,
                email = "test@example.com",
                emailVerificationCode = "123456",
            ),
        )

        val resolved = resolveRoutes(routes)
        assertEquals(1, resolved.size)
        val sheet = resolved.single()
        assertIs<AppRoute.Main.Sheet>(sheet)
        assertEquals(AppRoute.Sheets.ActivityHistory, sheet.initialRoute)
        assertEquals(2, sheet.innerRoutes.size)
        assertIs<AppRoute.Menu.MyAccount>(sheet.innerRoutes[0])
        assertIs<AppRoute.Verification>(sheet.innerRoutes[1])
    }

    @Test
    fun `routes before sheet stay on root backstack`() {
        val routes = listOf(
            AppRoute.Main.Scanner,
            AppRoute.Sheets.ActivityHistory,
            AppRoute.Token.Info(mint),
        )

        val resolved = resolveRoutes(routes)
        assertEquals(2, resolved.size)
        assertIs<AppRoute.Main.Scanner>(resolved[0])
        val sheet = resolved[1]
        assertIs<AppRoute.Main.Sheet>(sheet)
        assertEquals(AppRoute.Sheets.ActivityHistory, sheet.initialRoute)
        assertEquals(1, sheet.innerRoutes.size)
    }

    // endregion

    // region Equality for stack diffing

    @Test
    fun `resolved routes are structurally equal when inputs match`() {
        val routes = listOf(
            AppRoute.Sheets.ActivityHistory,
            AppRoute.Token.Info(mint, fromDeeplink = true),
        )

        val resolved1 = resolveRoutes(routes)
        val resolved2 = resolveRoutes(routes)
        assertEquals(resolved1, resolved2)
    }

    @Test
    fun `resolved routes differ when inner routes differ`() {
        val mintA = Mint("So11111111111111111111111111111111111111112")
        val mintB = Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")

        val resolvedA =
            resolveRoutes(listOf(AppRoute.Sheets.ActivityHistory, AppRoute.Token.Info(mintA)))
        val resolvedB =
            resolveRoutes(listOf(AppRoute.Sheets.ActivityHistory, AppRoute.Token.Info(mintB)))
        assert(resolvedA != resolvedB)
    }

    // endregion

    // region Tab homes must stay flat, not become sheets

    @Test
    fun `wallet tab stays flat and token info pushes on top`() {
        val routes = listOf(
            AppRoute.Sheets.Wallet,
            AppRoute.Token.Info(mint, fromDeeplink = true),
        )

        val resolved = resolveRoutes(routes)
        assertEquals(routes, resolved)
        assertTrue(resolved.none { it is AppRoute.Main.Sheet })
    }

    @Test
    fun `chats tab stays flat and the chat pushes on top`() {
        val routes = listOf(
            AppRoute.Sheets.Tips(),
            AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(ChatId(listOf(1, 2, 3, 4)))),
        )

        val resolved = resolveRoutes(routes)
        assertEquals(routes, resolved)
        assertTrue(resolved.none { it is AppRoute.Main.Sheet })
    }

    @Test
    fun `menu tab stays flat with my account and verification pushed on top`() {
        val routes = listOf(
            AppRoute.Sheets.Menu,
            AppRoute.Menu.MyAccount,
            AppRoute.Verification(
                origin = AppRoute.Menu.MyAccount,
                includePhone = false,
                email = "test@example.com",
                emailVerificationCode = "123456",
            ),
        )

        assertEquals(routes, resolveRoutes(routes))
    }

    @Test
    fun `a genuine sheet that follows a tab home still wraps`() {
        val routes = listOf(
            AppRoute.Sheets.Wallet,
            AppRoute.Token.Info(mint),
            AppRoute.Sheets.ActivityHistory,
        )

        val resolved = resolveRoutes(routes)
        assertEquals(3, resolved.size)
        assertEquals(AppRoute.Sheets.Wallet, resolved[0])
        assertIs<AppRoute.Token.Info>(resolved[1])
        val sheet = resolved[2]
        assertIs<AppRoute.Main.Sheet>(sheet)
        assertEquals(AppRoute.Sheets.ActivityHistory, sheet.initialRoute)
    }

    // endregion

    // region resolveBackStack — tab switch replaces the base stack

    @Test
    fun `a deeplink to a tab replaces the launch home rather than stacking on it`() {
        val base = listOf<NavKey>(AppRoute.Sheets.Wallet)
        val deeplink = listOf(
            AppRoute.Sheets.Tips(),
            AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(ChatId(listOf(9)))),
        )

        val stack = resolveBackStack(base, deeplink)
        assertEquals(deeplink, stack)
        // The Wallet home the app launched on must not linger beneath the Chats tab.
        assertTrue(stack.none { it == AppRoute.Sheets.Wallet })
    }

    @Test
    fun `a token deeplink lands on the wallet tab exactly once`() {
        val base = listOf<NavKey>(AppRoute.Sheets.Wallet)
        val deeplink = listOf(AppRoute.Sheets.Wallet, AppRoute.Token.Info(mint, fromDeeplink = true))

        val stack = resolveBackStack(base, deeplink)
        assertEquals(2, stack.size)
        assertEquals(1, stack.count { it == AppRoute.Sheets.Wallet })
        assertIs<AppRoute.Token.Info>(stack[1])
    }

    @Test
    fun `a deeplink without a tab home stacks on the launch home`() {
        val base = listOf<NavKey>(AppRoute.Sheets.Wallet)
        val deeplink = listOf(AppRoute.Token.Info(mint), AppRoute.Token.Swap(SwapPurpose.Buy(mint)))

        assertEquals(base + deeplink, resolveBackStack(base, deeplink))
    }

    // endregion
}
