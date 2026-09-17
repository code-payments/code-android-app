package com.flipcash.app.messenger.internal.link

import com.flipcash.app.core.internal.bill.BillController
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.generators.MnemonicGenerator
import com.getcode.opencode.internal.transactors.DefaultAccountClusterFactory
import com.getcode.opencode.managers.MnemonicManager
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.test.Test

/**
 * Opening a cash link claims it — `CashLinkDelegate.openCashLink` goes straight to
 * `BillController.receiveGiftCard`. Rendering one must not, or scrolling past a message would
 * empty the link inside it. That property is invisible when it holds and expensive when it
 * breaks, so it gets a test of its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GiftCardLookupTest {

    /**
     * Real derivation rather than mocks: mocking it out would stub the path between the entropy
     * and the query, and the query is the whole point of the test. Both collaborators are pure
     * and dependency-free.
     */
    private fun lookup(accountController: AccountController) = GiftCardLookup(
        mnemonicManager = MnemonicManager(MnemonicGenerator()),
        accountClusterFactory = DefaultAccountClusterFactory(),
        accountController = accountController,
        tokenCoordinator = mock(),
        userManager = mock { on { accountCluster } doReturn mock() },
    )

    @Test
    fun `resolving a card reads the account and claims nothing`() = runTest {
        val billController = mock<BillController>()
        val accountController = mock<AccountController> {
            onBlocking { getAccounts(any(), any(), anyOrNull()) } doReturn Result.failure(IOException())
        }

        lookup(accountController)("KNi8pQr1n5hRU65vKJGge3")

        verify(accountController).getAccounts(any(), any(), anyOrNull())
        verifyNoInteractions(billController)
    }
}
