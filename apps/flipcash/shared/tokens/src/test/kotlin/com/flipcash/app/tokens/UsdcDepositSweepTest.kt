package com.flipcash.app.tokens

import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.flipcash.libs.coroutines.DispatcherProvider
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
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class UsdcDepositSweepTest {

    private val transactionOperations: TransactionOperations = mockk(relaxed = true)
    private val accountController: AccountController = mockk(relaxed = true)
    private val tokenCoordinator: TokenCoordinator = mockk(relaxed = true)
    private val balancePoller: BalancePoller = mockk(relaxed = true)

    private val owner: AccountCluster = mockk(relaxed = true)

    private lateinit var sweep: UsdcDepositSweep

    @Before
    fun setUp() {
        coEvery {
            balancePoller.awaitBalanceChange(any(), any(), any(), any(), any())
        } returns Result.success(Fiat.Zero)

        sweep = UsdcDepositSweep(
            transactionOperations = transactionOperations,
            accountController = accountController,
            tokenCoordinator = tokenCoordinator,
            balancePoller = balancePoller,
            dispatchers = object : DispatcherProvider {
                override val Default = kotlinx.coroutines.Dispatchers.Default
                override val Main = kotlinx.coroutines.Dispatchers.Default
                override val IO = kotlinx.coroutines.Dispatchers.IO
            },
            maxRetries = 3,
            initialDelay = 10.milliseconds,
            backoffFactor = 1.0,
            pollInterval = 10.milliseconds,
            pollMaxAttempts = 2,
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
    fun `gives up after max retries when USDC account is not found`() = runTest {
        stubNoUsdcAccount()

        sweep.execute(owner)
        advanceUntilIdle()
        Thread.sleep(200)

        coVerify(exactly = 0) {
            transactionOperations.swapUsdc(any(), any())
        }
    }

    @Test
    fun `gives up after max retries when account type is not AssociatedToken`() = runTest {
        stubUsdcAccount(balance = 1_000_000L, type = AccountType.Primary)

        sweep.execute(owner)
        advanceUntilIdle()
        Thread.sleep(200)

        coVerify(exactly = 0) {
            transactionOperations.swapUsdc(any(), any())
        }
    }

    @Test
    fun `gives up after max retries when USDC balance stays zero`() = runTest {
        stubUsdcAccount(balance = 0L)

        sweep.execute(owner)
        advanceUntilIdle()
        Thread.sleep(200)

        coVerify(exactly = 0) {
            transactionOperations.swapUsdc(any(), any())
        }
    }

    @Test
    fun `retries until balance appears then sweeps`() = runTest {
        var callCount = 0
        coEvery {
            accountController.getAccount(any(), any(), any())
        } coAnswers {
            callCount++
            val balance = if (callCount >= 3) 2_000_000L else 0L
            val accountInfo = mockk<AccountInfo> {
                every { accountType } returns AccountType.AssociatedToken
                every { this@mockk.balance } returns balance
                every { address } returns mockk<PublicKey>(relaxed = true)
            }
            Result.success(accountInfo)
        }
        coEvery { transactionOperations.swapUsdc(any(), any()) } returns Result.success(Unit)

        sweep.execute(owner)
        advanceUntilIdle()
        Thread.sleep(300)

        coVerify {
            transactionOperations.swapUsdc(owner, 2_000_000L)
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
    fun `polls for USDF balance on successful swap`() = runTest {
        stubUsdcAccount(balance = 1_000_000L)
        coEvery { transactionOperations.swapUsdc(any(), any()) } returns Result.success(Unit)

        sweep.execute(owner)
        advanceUntilIdle()
        Thread.sleep(200)

        coVerify {
            balancePoller.awaitBalanceChange(
                mint = Mint.usdf,
                baseline = Fiat.Zero,
                predicate = any(),
                interval = 10.milliseconds,
                maxAttempts = 2,
            )
        }
    }

    @Test
    fun `does not poll for USDF balance when swap fails`() = runTest {
        stubUsdcAccount(balance = 1_000_000L)
        coEvery {
            transactionOperations.swapUsdc(any(), any())
        } returns Result.failure(RuntimeException("swap failed"))

        sweep.execute(owner)
        advanceUntilIdle()
        Thread.sleep(200)

        coVerify(exactly = 0) {
            balancePoller.awaitBalanceChange(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `completes gracefully when USDF balance poll times out`() = runTest {
        stubUsdcAccount(balance = 1_000_000L)
        coEvery { transactionOperations.swapUsdc(any(), any()) } returns Result.success(Unit)
        coEvery {
            balancePoller.awaitBalanceChange(any(), any(), any(), any(), any())
        } returns Result.failure(BalancePollError.Timeout(Mint.usdf, 2))

        sweep.execute(owner)
        advanceUntilIdle()
        Thread.sleep(200)

        coVerify {
            balancePoller.awaitBalanceChange(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `does not execute concurrently when job is active`() = runTest {
        stubUsdcAccount(balance = 1_000_000L)
        coEvery { transactionOperations.swapUsdc(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(500)
            Result.success(Unit)
        }

        sweep.execute(owner)
        sweep.execute(owner)

        Thread.sleep(700)

        coVerify(exactly = 1) {
            transactionOperations.swapUsdc(any(), any())
        }
    }

    @Test
    fun `cancel stops active job`() = runTest {
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
        Thread.sleep(50)
        sweep.cancel()
        Thread.sleep(100)

        coVerify(exactly = 0) {
            transactionOperations.swapUsdc(any(), any())
        }
    }
}
