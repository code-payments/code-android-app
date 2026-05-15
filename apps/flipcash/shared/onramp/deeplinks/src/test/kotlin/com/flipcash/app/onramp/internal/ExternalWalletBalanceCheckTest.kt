package com.flipcash.app.onramp.internal

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.onramp.deeplinks.ExternalWalletConnection
import com.flipcash.app.onramp.DeeplinkOnRampError
import com.flipcash.app.onramp.ExternalWalletOnRampController
import com.flipcash.app.onramp.ExternalWalletOnRampState
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.user.UserManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.rpc.RpcConfig
import com.solana.networking.HttpNetworkDriver
import com.solana.networking.HttpRequest
import dev.bmcreations.phantom.connect.wallet.PhantomWalletConnector
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

class ExternalWalletBalanceCheckTest {

    private val userManager = mockk<UserManager>(relaxed = true)
    private val userFlags = mockk<UserFlagsCoordinator>(relaxed = true)
    private val transactionController = mockk<TransactionOperations>(relaxed = true)
    private val networkDriver = mockk<HttpNetworkDriver>()
    private val rpcConfig = RpcConfig(networkDriver = networkDriver, rpcUrl = "https://localhost")
    private val phantomConnector = mockk<PhantomWalletConnector>(relaxed = true)

    private lateinit var controller: ExternalWalletOnRampController

    private val testPublicKey = PublicKey("11111111111111111111111111111111")

    @Before
    fun setUp() {
        controller = ExternalWalletOnRampController(
            userManager = userManager,
            userFlags = userFlags,
            transactionController = transactionController,
            rpcConfig = rpcConfig,
            phantomConnector = phantomConnector,
        )
    }

    private fun putControllerInConnectedState(requiredQuarks: Long = 10_000_000L) {
        controller.transitionTo(
            ExternalWalletOnRampState.Connected(
                origin = AppRoute.Token.CurrencyCreator,
                provider = OnRampProvider.Phantom,
                connection = ExternalWalletConnection(
                    publicKey = testPublicKey,
                    session = "test-session",
                ),
            )
        )
        controller.setAmount(
            VerifiedFiat(
                localFiat = LocalFiat(
                    usdf = Fiat(quarks = requiredQuarks),
                    nativeAmount = Fiat(quarks = requiredQuarks),
                )
            )
        )
    }

    private fun mockRpcResponses(solLamports: Long, usdcQuarks: Long) {
        coEvery { networkDriver.makeHttpRequest(any()) } answers {
            val body = firstArg<HttpRequest>().body.orEmpty()
            when {
                "getBalance" in body -> balanceJson(solLamports)
                "getTokenAccountBalance" in body -> tokenBalanceJson(usdcQuarks)
                else -> errorJson(-1, "unexpected method")
            }
        }
    }

    // ── Insufficient SOL ──

    @Test
    fun `insufficient SOL transitions to Failed with InsufficientSol`() = runTest {
        putControllerInConnectedState()
        mockRpcResponses(solLamports = 1_000L, usdcQuarks = 100_000_000L)

        controller.createAndValidateSwapTransaction()

        val state = controller.state.value
        assertTrue(state is ExternalWalletOnRampState.Failed, "Expected Failed, got $state")
        assertTrue(state.error is DeeplinkOnRampError.InsufficientSol, "Expected InsufficientSol, got ${state.error}")
    }

    @Test
    fun `zero SOL transitions to Failed with InsufficientSol`() = runTest {
        putControllerInConnectedState()
        mockRpcResponses(solLamports = 0L, usdcQuarks = 100_000_000L)

        controller.createAndValidateSwapTransaction()

        val state = controller.state.value
        assertTrue(state is ExternalWalletOnRampState.Failed)
        assertTrue(state.error is DeeplinkOnRampError.InsufficientSol)
    }

    // ── Insufficient USDC ──

    @Test
    fun `insufficient USDC transitions to Failed with InsufficientUsdc`() = runTest {
        putControllerInConnectedState(requiredQuarks = 10_000_000L)
        mockRpcResponses(solLamports = 10_000_000L, usdcQuarks = 1_000L)

        controller.createAndValidateSwapTransaction()

        val state = controller.state.value
        assertTrue(state is ExternalWalletOnRampState.Failed, "Expected Failed, got $state")
        assertTrue(state.error is DeeplinkOnRampError.InsufficientUsdc, "Expected InsufficientUsdc, got ${state.error}")
    }

    @Test
    fun `zero USDC transitions to Failed with InsufficientUsdc`() = runTest {
        putControllerInConnectedState(requiredQuarks = 10_000_000L)
        mockRpcResponses(solLamports = 10_000_000L, usdcQuarks = 0L)

        controller.createAndValidateSwapTransaction()

        val state = controller.state.value
        assertTrue(state is ExternalWalletOnRampState.Failed)
        assertTrue(state.error is DeeplinkOnRampError.InsufficientUsdc)
    }

    // ── RPC errors wrap as FailedToCreateTransaction ──

    @Test
    fun `RPC error on getBalance transitions to Failed with FailedToCreateTransaction`() = runTest {
        putControllerInConnectedState()
        coEvery { networkDriver.makeHttpRequest(any()) } returns errorJson(-1, "Internal error")

        controller.createAndValidateSwapTransaction()

        val state = controller.state.value
        assertTrue(state is ExternalWalletOnRampState.Failed, "Expected Failed, got $state")
        assertTrue(
            state.error is DeeplinkOnRampError.FailedToCreateTransaction,
            "Expected FailedToCreateTransaction, got ${state.error}"
        )
    }

    // ── Boundary: exact threshold passes ──

    @Test
    fun `exact SOL threshold passes balance check`() = runTest {
        putControllerInConnectedState(requiredQuarks = 5_000_000L)
        // 6_500_000 is MINIMUM_SOL_LAMPORTS, 5_000_000 quarks matched exactly
        mockRpcResponses(solLamports = 6_500_000L, usdcQuarks = 5_000_000L)

        controller.createAndValidateSwapTransaction()

        val state = controller.state.value
        if (state is ExternalWalletOnRampState.Failed) {
            assertTrue(
                state.error !is DeeplinkOnRampError.InsufficientSol &&
                    state.error !is DeeplinkOnRampError.InsufficientUsdc,
                "Should not fail with balance errors, got ${state.error}"
            )
        }
    }

    @Test
    fun `SOL just below threshold fails with InsufficientSol`() = runTest {
        putControllerInConnectedState()
        mockRpcResponses(solLamports = 6_499_999L, usdcQuarks = 100_000_000L)

        controller.createAndValidateSwapTransaction()

        val state = controller.state.value
        assertTrue(state is ExternalWalletOnRampState.Failed)
        assertTrue(state.error is DeeplinkOnRampError.InsufficientSol)
    }

    @Test
    fun `USDC exactly matching required amount passes balance check`() = runTest {
        putControllerInConnectedState(requiredQuarks = 10_000_000L)
        mockRpcResponses(solLamports = 10_000_000L, usdcQuarks = 10_000_000L)

        controller.createAndValidateSwapTransaction()

        val state = controller.state.value
        if (state is ExternalWalletOnRampState.Failed) {
            assertTrue(
                state.error !is DeeplinkOnRampError.InsufficientSol &&
                    state.error !is DeeplinkOnRampError.InsufficientUsdc,
                "Should not fail with balance errors, got ${state.error}"
            )
        }
    }

    @Test
    fun `USDC one quark below required fails with InsufficientUsdc`() = runTest {
        putControllerInConnectedState(requiredQuarks = 10_000_000L)
        mockRpcResponses(solLamports = 10_000_000L, usdcQuarks = 9_999_999L)

        controller.createAndValidateSwapTransaction()

        val state = controller.state.value
        assertTrue(state is ExternalWalletOnRampState.Failed)
        assertTrue(state.error is DeeplinkOnRampError.InsufficientUsdc)
    }

    companion object {
        private fun balanceJson(lamports: Long) =
            """{"jsonrpc":"2.0","result":{"context":{"slot":1},"value":$lamports},"id":"1"}"""

        private fun tokenBalanceJson(quarks: Long) =
            """{"jsonrpc":"2.0","result":{"context":{"slot":1},"value":{"amount":"$quarks","decimals":6,"uiAmount":${quarks / 1_000_000.0},"uiAmountString":"${quarks / 1_000_000.0}"}},"id":"1"}"""

        private fun errorJson(code: Int, message: String) =
            """{"jsonrpc":"2.0","error":{"code":$code,"message":"$message"},"id":"1"}"""
    }
}
