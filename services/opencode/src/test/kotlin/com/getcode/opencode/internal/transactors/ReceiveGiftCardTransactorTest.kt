package com.getcode.opencode.internal.transactors

import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.managers.GiftCardManager
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountResponse
import com.getcode.opencode.model.core.errors.SubmitIntentError
import com.getcode.opencode.model.financial.DataSource
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenResult
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.providers.TokenMetadataProvider
import com.getcode.solana.keys.Key32
import com.getcode.solana.keys.Mint
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertSame
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

    // endregion

    // region intent submission

    @Test
    fun `start fails with the stale state when the gift card was already claimed`() = runTest {
        setupWithOwner()
        setupClaimableGiftCard()
        val alreadyClaimed = SubmitIntentError.StaleState(
            listOf("gift card balance has already been claimed")
        )
        coEvery {
            transactionController.receiveRemotely(any(), any(), any(), any())
        } returns Result.failure(alreadyClaimed)

        val result = transactor.start(claimIfOwned = false)

        assertTrue(result.isFailure)
        assertSame(alreadyClaimed, result.exceptionOrNull())
    }

    @Test
    fun `start fails when the receive intent is rejected`() = runTest {
        setupWithOwner()
        setupClaimableGiftCard()
        val denied = SubmitIntentError.Denied(listOf("denied"))
        coEvery {
            transactionController.receiveRemotely(any(), any(), any(), any())
        } returns Result.failure(denied)

        val result = transactor.start(claimIfOwned = false)

        assertTrue(result.isFailure)
        assertSame(denied, result.exceptionOrNull())
    }

    // endregion

    // region dispose

    @Test
    fun `dispose clears state so subsequent start fails`() = runTest {
        setupWithOwner()

        transactor.dispose()

        val result = transactor.start(claimIfOwned = false)
        assertTrue(result.isFailure)
    }

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

    private fun setupClaimableGiftCard() {
        val mint = Mint("giftMint11111111111111111111111111111111111")
        val info = mockk<AccountInfo>(relaxed = true) {
            every { claimState } returns AccountInfo.ClaimState.NotClaimed
            every { isGiftCardIssuer } returns false
            every { this@mockk.mint } returns mint
            every { originalExchangeData } returns ExchangeData.WithRate(
                currencyCode = "usd",
                exchangeRate = 1.0,
                nativeAmount = 5.0,
                quarks = 5_000_000,
                mint = mint,
            )
        }
        val token = mockk<Token>(relaxed = true) {
            every { address } returns mint
        }
        coEvery {
            accountController.getAccounts(any(), any(), any())
        } returns Result.success(AccountResponse(mapOf(Key32.mock to info)))
        coEvery { tokenProvider.getTokenMetadata(mint) } returns
            Result.success(TokenResult(token, DataSource.Network))
        every { accountController.hasAccountFor(mint) } returns true
    }

    // endregion
}
