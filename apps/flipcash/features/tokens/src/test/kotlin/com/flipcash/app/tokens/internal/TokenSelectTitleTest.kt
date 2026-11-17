package com.flipcash.app.tokens.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.core.tokens.TokenPurpose
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Under the Group Money shell, "N currencies >" on the Money tab opens a screen titled Currencies, as
 * iOS does. Every other use of the screen keeps the picker's title.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TokenSelectTitleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun title(purpose: TokenPurpose, shell: NavBarShell): String =
        context.getString(tokenSelectTitle(purpose, shell))

    @Test
    fun `money's currency count opens Currencies`() {
        assertEquals("Currencies", title(TokenPurpose.Balance, NavBarShell.GroupMoney))
    }

    @Test
    fun `the classic wallet's balance still opens Select Currency`() {
        assertEquals("Select Currency", title(TokenPurpose.Balance, NavBarShell.Classic))
    }

    @Test
    fun `pickers under the shell keep Select Currency`() {
        assertEquals("Select Currency", title(TokenPurpose.Select, NavBarShell.GroupMoney))
        assertEquals("Select Currency", title(TokenPurpose.Withdraw, NavBarShell.GroupMoney))
        assertEquals("Select Currency", title(TokenPurpose.Deposit, NavBarShell.GroupMoney))
    }

    @Test
    fun `payment pickers keep Select Payment Currency under either shell`() {
        val swap = TokenPurpose.Swap(desiredToken = Token.usdf.address, amount = Fiat.Zero)
        val launch = TokenPurpose.LaunchFunding(amount = Fiat.Zero)
        for (shell in NavBarShell.entries) {
            assertEquals("Select Payment Currency", title(swap, shell))
            assertEquals("Select Payment Currency", title(launch, shell))
        }
    }
}
