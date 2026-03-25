package com.getcode.opencode.internal.transactors

import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.managers.GiftCardManager
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.providers.TokenMetadataProvider
import com.getcode.solana.keys.Key32
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveGiftCardTransactorTest {

    private val accountController = mockk<AccountController>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val tokenProvider = mockk<TokenMetadataProvider>(relaxed = true)
    private val mnemonicManager = mockk<MnemonicManager>(relaxed = true)
    private val giftCardManager = mockk<GiftCardManager>(relaxed = true)

    private val accountClusterFactory = AccountClusterFactory { mockk(relaxed = true) }

    private val transactor = ReceiveGiftCardTransactor(
        accountController = accountController,
        transactionController = transactionController,
        tokenProvider = tokenProvider,
        mnemonicManager = mnemonicManager,
        giftCardManager = giftCardManager,
        accountClusterFactory = accountClusterFactory,
    )

    @Before
    fun setUp() {
        mockkStatic("com.getcode.utils.LoggingKt")
        every { com.getcode.utils.trace(any(), any(), any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkStatic("com.getcode.utils.LoggingKt")
    }

    // region preconditions

    @Test
    fun `start fails when with() not called (no owner)`() = runTest {
        val result = transactor.start(claimIfOwned = false)

        assertTrue(result.isFailure)
    }

    @Test
    fun `start fails when with() called but account query crashes`() = runTest {
        setupWithOwner()

        // MockK has a known bug returning Result<T> from suspend functions (inline class boxing).
        // The relaxed mock's default return for getAccounts produces a corrupted Result, which
        // causes a ClassCastException inside start() — verifying the error propagates.
        val result = runCatching { transactor.start(claimIfOwned = false) }

        assertTrue(result.isFailure || result.getOrNull()?.isFailure == true)
    }

    // Note: Tests that require `accountController.getAccounts()` to return specific
    // Result.success(...) values are blocked by a MockK bug with Result<T> inline class
    // returns on suspend functions (https://github.com/mockk/mockk/issues/937).
    // Those tests should use fake repositories + real controllers when the project
    // establishes that pattern for transactor-level tests.

    // endregion

    // region helpers

    private fun setupWithOwner() {
        val owner = mockk<AccountCluster>(relaxed = true) {
            every { authority } returns mockk(relaxed = true) {
                every { keyPair } returns mockk(relaxed = true)
            }
            every { withTimelockForToken(any<Token>()) } returns this
            every { vaultPublicKey } returns Key32.mock
        }
        every { mnemonicManager.fromEntropyBase58(any()) } returns mockk(relaxed = true)

        transactor.with(owner, "test-entropy")
    }

    // endregion
}
