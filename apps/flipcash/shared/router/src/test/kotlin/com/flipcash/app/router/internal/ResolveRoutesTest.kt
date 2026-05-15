package com.flipcash.app.router.internal

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.extensions.resolveRoutes
import com.getcode.solana.keys.Mint
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ResolveRoutesTest {

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

    // region Sheet wrapping

    @Test
    fun `single sheet route is wrapped in Main Sheet`() {
        val resolved = resolveRoutes(listOf(AppRoute.Sheets.Wallet))
        assertEquals(1, resolved.size)
        val sheet = resolved.single()
        assertIs<AppRoute.Main.Sheet>(sheet)
        assertEquals(AppRoute.Sheets.Wallet, sheet.initialRoute)
        assertEquals(emptyList(), sheet.innerRoutes)
    }

    @Test
    fun `sheet with inner routes bundles into Main Sheet`() {
        val mint = Mint("So11111111111111111111111111111111111111112")
        val routes = listOf(
            AppRoute.Sheets.Wallet,
            AppRoute.Token.Info(mint, fromDeeplink = true),
        )

        val resolved = resolveRoutes(routes)
        assertEquals(1, resolved.size)
        val sheet = resolved.single()
        assertIs<AppRoute.Main.Sheet>(sheet)
        assertEquals(AppRoute.Sheets.Wallet, sheet.initialRoute)
        assertEquals(1, sheet.innerRoutes.size)
        assertIs<AppRoute.Token.Info>(sheet.innerRoutes[0])
    }

    @Test
    fun `sheet with multiple inner routes bundles all`() {
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

        val resolved = resolveRoutes(routes)
        assertEquals(1, resolved.size)
        val sheet = resolved.single()
        assertIs<AppRoute.Main.Sheet>(sheet)
        assertEquals(AppRoute.Sheets.Menu, sheet.initialRoute)
        assertEquals(2, sheet.innerRoutes.size)
        assertIs<AppRoute.Menu.MyAccount>(sheet.innerRoutes[0])
        assertIs<AppRoute.Verification>(sheet.innerRoutes[1])
    }

    @Test
    fun `routes before sheet stay on root backstack`() {
        val routes = listOf(
            AppRoute.Main.Scanner,
            AppRoute.Sheets.Wallet,
            AppRoute.Token.Info(Mint("So11111111111111111111111111111111111111112")),
        )

        val resolved = resolveRoutes(routes)
        assertEquals(2, resolved.size)
        assertIs<AppRoute.Main.Scanner>(resolved[0])
        val sheet = resolved[1]
        assertIs<AppRoute.Main.Sheet>(sheet)
        assertEquals(AppRoute.Sheets.Wallet, sheet.initialRoute)
        assertEquals(1, sheet.innerRoutes.size)
    }

    // endregion

    // region Equality for stack diffing

    @Test
    fun `resolved routes are structurally equal when inputs match`() {
        val mint = Mint("So11111111111111111111111111111111111111112")
        val routes = listOf(
            AppRoute.Sheets.Wallet,
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

        val resolvedA = resolveRoutes(listOf(AppRoute.Sheets.Wallet, AppRoute.Token.Info(mintA)))
        val resolvedB = resolveRoutes(listOf(AppRoute.Sheets.Wallet, AppRoute.Token.Info(mintB)))
        assert(resolvedA != resolvedB)
    }

    // endregion
}
