package com.flipcash.app.tokens

import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.solana.keys.PublicKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsdcDepositSweepTest {

    private val transactionOperations: TransactionOperations = mockk(relaxed = true)
    private val accountController: AccountController = mockk(relaxed = true)
    private val tokenCoordinator: TokenCoordinator = mockk(relaxed = true)

    private val owner: AccountCluster = mockk(relaxed = true)

    private lateinit var sweep: UsdcDepositSweep

    @Before
    fun setUp() {
        sweep = UsdcDepositSweep(
            transactionOperations = transactionOperations,
            accountController = accountController,
            tokenCoordinator = tokenCoordinator,
        )
    }

    @After
    fun tearDown() {
        sweep.cancel()
    }

    private fun stubUsdcAccount(balance: Long, type: AccountType = AccountType.AssociatedToken) {
        val accountInfo = mockk<AccountInfo> {
            every { accountType } returns type
            every { this@mockk.balance } returns balance
            every { address } returns mockk<PublicKey>(relaxed = true)
        }
        coEvery {
            accountController.getAccount(any(), any(), any())
        } returns Result.success(accountInfo)
    }

    private fun stubNoUsdcAccount() {
        coEvery {
            accountController.getAccount(any(), any(), any())
        } returns Result.failure(RuntimeException("not found"))
    }

    @Test
    fun `skips swap when USDC account is not found`() = runTest {
        stubNoUsdcAccount()

        sweep.execute(owner)
        advanceUntilIdle()

        // Give the internal scope time to complete
        Thread.sleep(100)

        coVerify(exactly = 0) {
            transactionOperations.swapUsdc(any(), any())
        }
    }

    @Test
    fun `skips swap when USDC account type is not AssociatedToken`() = runTest {
        stubUsdcAccount(balance = 1_000_000L, type = AccountType.Primary)

        sweep.execute(owner)
        advanceUntilIdle()
        Thread.sleep(100)

        coVerify(exactly = 0) {
            transactionOperations.swapUsdc(any(), any())
        }
    }

    @Test
    fun `skips swap when USDC balance is zero`() = runTest {
        stubUsdcAccount(balance = 0L)

        sweep.execute(owner)
        advanceUntilIdle()
        Thread.sleep(100)

        coVerify(exactly = 0) {
            transactionOperations.swapUsdc(any(), any())
        }
    }

    @Test
    fun `skips swap when USDC balance is negative`() = runTest {
        stubUsdcAccount(balance = -1L)

        sweep.execute(owner)
        advanceUntilIdle()
        Thread.sleep(100)

        coVerify(exactly = 0) {
            transactionOperations.swapUsdc(any(), any())
        }
    }

    @Test
    fun `calls swapUsdc with correct amount when balance is positive`() = runTest {
        val amount = 5_000_000L
        stubUsdcAccount(balance = amount)
        coEvery { transactionOperations.swapUsdc(any(), any()) } returns Result.success(Unit)

        sweep.execute(owner)
        advanceUntilIdle()
        Thread.sleep(200)

        coVerify {
            transactionOperations.swapUsdc(owner, amount)
        }
    }

    @Test
    fun `calls tokenCoordinator update on successful swap`() = runTest {
        stubUsdcAccount(balance = 1_000_000L)
        coEvery { transactionOperations.swapUsdc(any(), any()) } returns Result.success(Unit)

        sweep.execute(owner)
        advanceUntilIdle()
        Thread.sleep(200)

        coVerify {
            tokenCoordinator.update()
        }
    }

    @Test
    fun `does not call tokenCoordinator update on failed swap`() = runTest {
        stubUsdcAccount(balance = 1_000_000L)
        coEvery {
            transactionOperations.swapUsdc(any(), any())
        } returns Result.failure(RuntimeException("swap failed"))

        sweep.execute(owner)
        advanceUntilIdle()
        Thread.sleep(200)

        coVerify(exactly = 0) {
            tokenCoordinator.update()
        }
    }

    @Test
    fun `does not execute concurrently when job is active`() = runTest {
        stubUsdcAccount(balance = 1_000_000L)
        coEvery { transactionOperations.swapUsdc(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(500)
            Result.success(Unit)
        }

        // First call starts the job
        sweep.execute(owner)
        // Second call should be ignored since the first is still active
        sweep.execute(owner)

        Thread.sleep(700)

        coVerify(exactly = 1) {
            transactionOperations.swapUsdc(any(), any())
        }
    }

    @Test
    fun `cancel stops active job`() = runTest {
        // Make getAccount slow so we can cancel before swapUsdc is reached
        coEvery {
            accountController.getAccount(any(), any(), any())
        } coAnswers {
            kotlinx.coroutines.delay(5000)
            val accountInfo = mockk<AccountInfo> {
                every { accountType } returns AccountType.AssociatedToken
                every { balance } returns 1_000_000L
                every { address } returns mockk<PublicKey>(relaxed = true)
            }
            Result.success(accountInfo)
        }
        coEvery { transactionOperations.swapUsdc(any(), any()) } returns Result.success(Unit)

        sweep.execute(owner)
        Thread.sleep(50) // Let the coroutine start
        sweep.cancel()
        Thread.sleep(100)

        coVerify(exactly = 0) {
            transactionOperations.swapUsdc(any(), any())
        }
    }
}
